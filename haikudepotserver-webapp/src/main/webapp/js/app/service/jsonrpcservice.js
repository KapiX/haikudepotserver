/*
 * Copyright 2013-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This service provides JSON-RPC functionality on top of the AngularJS $http service.</p>
 */

// see http://www.jsonrpc.org/specification

angular.module('haikudepotserver').factory('jsonRpc',
    [
        '$log','$http','$q',
        function($log,$http,$q) {

            var JsonRpcService = {

                errorCodes : {
                    PARSEERROR : -32700,
                    INVALIDREQUEST : -32600,
                    METHODNOTFOUND : -32601,
                    INVALIDPARAMETERS : -32602,
                    INTERNALERROR : -32603,
                    TRANSPORTFAILURE : -32100,
                    INVALIDRESPONSE : -32101,

                    VALIDATION : -32800,
                    OBJECTNOTFOUND : -32801,
                    CAPTCHABADRESPONSE : -32802,
                    AUTHORIZATIONFAILURE : -32803,
                    BADPKGICON : -32804,
                    AUTHORIZATIONRULECONFLICT : -32806
                },

                /**
                 * <p>This is a map of HTTP headers that is sent on each JSON-RPC request into the server.</p>
                 */

                headers : {},

                /**
                 * <p>This method will set the HTTP hder that is sent on each JSON-RPC request.  This is handy,
                 * for example for authentication.</p>
                 */

                setHeader : function(name, value) {

                    if(!name || 0==''+name.length) {
                        throw Error('the name of the http header is required');
                    }

                    if(!value || 0==''+value.length) {
                        delete JsonRpcService.headers[name];
                    }
                    else {
                        JsonRpcService.headers[name] = value;
                    }

                },

                /**
                 * <p>This counter is used to generate an id which can be used to identify a request-response method
                 * invocation in the json-rpc potocol.</p>
                 */

                counter : 1000,

                /**
                 * <p>This function will call a json-rpc method on a remote system identified by the supplied endpoint.
                 * If no id is supplied then it will fabricate one.  If there are no parameters supplied then it will
                 * send an empty array of parameters.  This method will return a promise that is fulfilled when the
                 * remote server has responded.</p>
                 */

                call : function(endpoint, method, params, id) {

                    if(!endpoint) {
                        throw Error('the endpoint is required to invoke a json-rpc method');
                    }

                    if(!method) {
                        throw Error('the method is required to invoke a json-rpc method');
                    }

                    if(!params) {
                        params = [];
                    }

                    if(!id) {
                        id = JsonRpcService.counter;
                        JsonRpcService.counter += 1;
                    }

                    function mkTransportErr(httpStatus) {
                       return mkErr(httpStatus,JsonRpcService.errorCodes.TRANSPORTFAILURE,'transport-failure');
                    }

                    function mkErr(httpStatus,code,message) {
                        return {
                            jsonrpc: "2.0",
                            id : id,
                            error : {
                                code : code,
                                message : message,
                                data : httpStatus
                            }
                        };
                    }

                    return $http({
                        cache: false,
                        method: 'POST',
                        url: endpoint,
                        headers: _.extend(
                            { 'Content-Type' : 'application/json' },
                            JsonRpcService.headers),
                        data: {
                            jsonrpc : "2.0",
                            method : method,
                            params : params,
                            id : id
                        }
                    }).then(
                        function successCallback(response) {
                            if(200 != response.status) {
                                return $q.reject(mkTransportErr(response.status));
                            }

                            if(!response.data.result) {
                                if (!response.data.error) {
                                    return $q.reject(mkErr(response.status, JsonRpcService.errorCodes.INVALIDRESPONSE, 'invalid-response'));
                                }

                                return $q.reject(response.data.error);
                            }

                            return response.data.result;
                        },
                        function errorCallback(response) {
                            return $q.reject(mkTransportErr(response.status));
                        }
                    );
                }

            };

            return JsonRpcService;

        }
    ]
);