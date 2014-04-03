/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive renders a small block of HTML that represents the breadcrumbs.</p>
 */

angular.module('haikudepotserver').directive('breadcrumbs',function() {
    return {
        restrict: 'E',
        templateUrl:'/js/app/directive/breadcrumbs.html',
        replace: true,
        scope: {
        },
        controller:
            ['$scope','$location','breadcrumbs','$rootScope',
                function($scope,$location,breadcrumbs,$rootScope) {

                    $scope.stack = breadcrumbs.stack();

                    function isItemActive(item) {
                        if(!item) {
                            return false;
                        }
                        return item.path == $location.path();
                    }

                    $scope.shouldShowItems = function() {
                        return $scope.stack;
                    };

                    $scope.goItem = function(item) {
                        breadcrumbs.popAndNavigateTo(item);

                        if(item.search) {
                            $location.search(item.search);
                        }

                        return false;
                    };

                    $scope.isItemActive = function(item) {
                        return isItemActive(item);
                    };

                    $scope.listItemClass = function(item) {
                        if(isItemActive(item)) {
                            return [ 'active' ];
                        }

                        return [];
                    }

                    $rootScope.$on(
                        "breadcrumbChangeSuccess",
                        function(stack) {
                            $scope.stack = breadcrumbs.stack();
                        }
                    );

                }
            ]
    };
});