/*
 * Copyright 2018 Red Hat Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
'use strict';

var qdr = require("./qdr.js");
var myutils = require("./utils.js");
var log = require("./log.js").logger();

const ID_QUALIFIER = 'ragent-'

function matches_qualifier (record) {
    return record.name && record.name.indexOf(ID_QUALIFIER) === 0;
}

function address_compare (a, b) {
    return myutils.string_compare(a.prefix, b.prefix);
}

function same_address_definition (a, b) {
    return a.prefix === b.prefix && a.distribution === b.distribution && a.waypoint === b.waypoint;
}

function autolink_compare (a, b) {
    return myutils.string_compare(a.addr, b.addr) || myutils.string_compare(a.direction, b.direction);
}

function same_autolink_definition (a, b) {
    return a.addr === b.addr && a.direction === b.direction && a.containerId === b.containerId;
}

function linkroute_compare (a, b) {
    return myutils.string_compare(a.prefix, b.prefix) || myutils.string_compare(a.direction, b.direction);
}

function same_linkroute_definition (a, b) {
    return a.prefix === b.prefix && a.direction === b.direction && a.containerId === b.containerId;
}

const entities = [
    {name:'addresses', comparator:address_compare, equality:same_address_definition, type:'org.apache.qpid.dispatch.router.config.address', singular:'address'},
    {name:'autolinks', comparator:autolink_compare, equality:same_autolink_definition, type:'org.apache.qpid.dispatch.router.config.autoLink', singular:'autolink'},
    {name:'linkroutes', comparator:linkroute_compare, equality:same_linkroute_definition, type:'org.apache.qpid.dispatch.router.config.linkRoute', singular:'linkroute'}
];

const directions = ['in', 'out'];

function RouterConfig(prefix) {
    this.prefix = prefix;
    this.autolinks = [];
    this.addresses = [];
    this.linkroutes = [];
}

RouterConfig.prototype.add_address = function (a) {
    this.addresses.push(myutils.merge({name:this.prefix + a.prefix}, a));
};

RouterConfig.prototype.add_autolink = function (a) {
    this.autolinks.push(myutils.merge({name:this.prefix + a.addr + '-' + a.direction}, a));
};

RouterConfig.prototype.add_linkroute = function (l) {
    this.linkroutes.push(myutils.merge({name:this.prefix + l.prefix + '-' + l.direction}, l));
};

RouterConfig.prototype.add_autolink_pair = function (def) {
    for (let i = 0; i < directions.length; i++) {
        this.add_autolink(myutils.merge({direction:directions[i]}, def));
    }
};

RouterConfig.prototype.add_linkroute_pair = function (def) {
    for (let i = 0; i < directions.length; i++) {
        this.add_linkroute(myutils.merge({direction:directions[i]}, def));
    }
};

RouterConfig.prototype.size = function () {
    var config = this;
    return entities.map(function (entity) {
        return config[entity.name].length;
    }).reduce(function (a, b) { return a + b; }, 0);
};

function get_router_id (router) {
    return router.connection ? router.connection.container_id : 'unknown-router';
}

function sort_config (config) {
    entities.forEach(function (entity) {
        config[entity.name].sort(entity.comparator);
    });
};

function delete_config (config, router) {
    let router_id = get_router_id(router);
    var promises = [];
    entities.forEach(function (entity) {
        config[entity.name].forEach(function (obj) {
            log.debug('deleting %s from %s: %s', entity.singular, router_id, obj.name);
            promises.push(router.delete_entity(entity.type, obj.name));
        });
    });
    return Promise.all(promises);
};

function create_config (config, router) {
    let router_id = get_router_id(router);
    var promises = [];
    entities.forEach(function (entity) {
        config[entity.name].forEach(function (obj) {
            log.debug('creating %s on %s: %j', entity.singular, router_id, obj);
            promises.push(router.create_entity(entity.type, obj.name, obj));
        });
    });
    return Promise.all(promises);
};

function retrieve_config(config, router) {
    let router_id = get_router_id(router);
    var errors = {};
    return Promise.all(entities.map(function (entity) {
        return router.query(entity.type).then(function (results) {
            log.debug('retrieved %s from %s', entity.name, router_id);
            config[entity.name] = results;
        }).catch(function (error) {
            log.error('error retrieving %s from %s: %s', entity.name, router_id, error);
            errors[entity.name] = error;
        });
    })).then(function () {
        sort_config(config);
        return errors;
    });
}

function apply_config(desired, router) {
    let router_id = get_router_id(router);
    log.debug('applying %j to %s', desired, router_id);
    var actual = new RouterConfig();
    return retrieve_config(actual, router).then(function (errors) {
        log.debug('retrieved actual config: %j', actual);
        var missing = new RouterConfig();
        var stale = new RouterConfig();

        function retrieved_without_error(entity) {
            return errors[entity.name] === undefined;
        }
        entities.filter(retrieved_without_error).forEach(function (entity) {
            var delta = myutils.changes(actual[entity.name], desired[entity.name], entity.comparator, entity.equality);
            if (delta) {
                log.info('updating configuration of %s on %s: %s', entity.name, router_id, delta.description);
                stale[entity.name] = delta.removed.filter(matches_qualifier).concat(delta.modified);
                missing[entity.name] = delta.added.concat(delta.modified);
            } else {
                log.debug('configuration of %s is up to date on %s', entity.name, router_id);
            }
        });

        if (missing.size() || stale.size() || Object.keys(errors).length) {
            var work = Promise.all([delete_config(stale, router), create_config(missing, router)]);
            return work.then(function () {
                return apply_config(desired, router);
            }).catch(function (error) {
                log.error('error while applying address configuration to %s, retrying: %s', router_id, error);
                return apply_config(desired, router);
            });
        } else {
            log.debug('config applied on %s: %j', router_id, actual);
            return actual;
        }
    }).catch(function (error) {
        log.error('failed to apply config on %s: %s', router_id, error);
    });
}

function desired_address_config(high_level_address_definitions) {
    var config = new RouterConfig(ID_QUALIFIER);
    for (var i in high_level_address_definitions) {
        var def = high_level_address_definitions[i];
        if (def.type === 'queue') {
            config.add_address({prefix:def.address, distribution:'balanced', waypoint:true});
            config.add_autolink_pair({addr:def.address, containerId: /*def.allocated_to ||*/ def.address});
        } else if (def.type === 'topic') {
            config.add_linkroute_pair({prefix:def.address, containerId: /*def.allocated_to ||*/ def.address});
        } else if (def.type === 'anycast') {
            config.add_address({prefix:def.address, distribution:'balanced', waypoint:false});
        } else if (def.type === 'multicast') {
            config.add_address({prefix:def.address, distribution:'multicast', waypoint:false});
        }
    }
    sort_config(config);
    log.debug('mapped %j => %j', high_level_address_definitions, config);
    return config;
}

module.exports = {
    realise_address_definitions: function (high_level_address_definitions, router) {
        try {
            return apply_config(desired_address_config(high_level_address_definitions), router);
        } catch (error) {
            log.error('error realising address definitions: %s', error);
        }
    }
};
