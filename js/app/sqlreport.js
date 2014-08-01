/*global angular */
var sqlApp = angular.module('SQLReport', ['ng', 'ngRoute']);

sqlApp.config(function ($routeProvider, $locationProvider) {
    "use strict";
    $locationProvider
        .html5Mode(true).hashPrefix('!');
    $routeProvider
        .when('/', {
            templateUrl: '/template/queries.html',
            controller: 'QueryCtrl'
        })
        .when('/summary', {
            templateUrl: '/template/summary.html',
            controller: 'SummaryCtrl'
        })
});