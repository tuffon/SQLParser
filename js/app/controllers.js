
sqlApp.controller('NavCtrl', function($scope, $location){
    "use strict";
    $scope.isActive = function(tab){
        return tab == $location.path();
    }
});


sqlApp.controller('QueryCtrl', function($scope, $http){
    "use strict";

    $http.get('/data/queries.json').success(function(data){
        console.log(data);
        $scope.rows = data.queries;
    });
});

sqlApp.controller('SummaryCtrl', function($scope, $location){
    "use strict";

    console.log('hey!');
});