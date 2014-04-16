/**
 * Where everything starts
 * - Register drag and drop.
 * - Clear out all gobal variables and reset them to blank.
 */
function loadOncoprintView(){
    oncoprintView.clear_high_dimensional_input('divIndependentVariable');
    oncoprintView.register_drag_drop();
}

function loadOncoprintOutput() {

    // Store the currently selected options as global variables;
    window.cancer_study_id_selected = 'coadread_tcga_pub';
    window.case_set_id_selected = 'coadread_tcga_pub_3way_complete';
    window.gene_set_id_selected = 'user-defined-list';
    window.tab_index = 'tab_visualize';
    window.zscore_threshold = '2.0';
    window.rppa_score_threshold = '2.0';

    //  Store the currently selected genomic profiles within an associative array
    window.genomic_profile_id_selected = new Array();
    window.genomic_profile_id_selected['coadread_tcga_pub_mutations']=1;
    window.genomic_profile_id_selected['coadread_tcga_pub_mrna_median_Zscores']=1;
    window.genomic_profile_id_selected['coadread_tcga_pub_gistic']=1;

    window.PortalGlobals = {
        getCancerStudyId: function() { return 'coadread_tcga_pub'},
        getGenes: function() { return 'AURKA TP53'},  // raw gene list (as it is entered by the user, it MAY CONTAIN onco query language)
        getGeneListString: function() {  // gene list WITHOUT onco query language
            return 'AURKA TP53'
        },
        getCaseSetId: function() { return 'coadread_tcga_pub_3way_complete';},  //Id for user chosen standard case set
        getCaseSetName: function() { return 'All Complete Tumors'},  //Name for user chose standard case set
        getCaseIdsKey: function() { return '8970e414d494103c2b67131214493fd1'; },   //A key arrsigned to use build case set
        getCases: function() { return ''; }, // list of queried case ids
        getOqlString: (function() {     // raw gene list (as it is entered by the user, it may contain onco query language)
            var oql = 'AURKA TP53'
                .replace("&gt;", ">", "gm")
                .replace("&lt;", "<", "gm")
                .replace("&eq;", "=", "gm")
                .replace(/[\r\n]/g, "\\n");
            return function() { return oql; };
        })(),
        getGeneticProfiles: function() { return 'coadread_tcga_pub_mutations coadread_tcga_pub_mrna_median_Zscores coadread_tcga_pub_gistic'; },
        getZscoreThreshold: function() { return '2.0'; },
        getRppaScoreThreshold: function() { return '2.0'; }
    };

    // This lets require.js execute the oncoprint code, starting at main-boilerplate.js
    var s = document.createElement('script');
    s.setAttribute('data-main', pageInfo.basePath + '/js/oncoprint/main-boilerplate.js');
    s.type = 'text/javascript';
    s.src = pageInfo.basePath + '/js/oncoprint/require.js';
    document.body.appendChild(s);
}

// constructor
var OncoprintView = function () {
    RmodulesView.call(this);
}

// inherit RmodulesView
OncoprintView.prototype = new RmodulesView();

// correct the pointer
OncoprintView.prototype.constructor = OncoprintView;

// submit analysis job
OncoprintView.prototype.submit_job = function () {
    var job = this;

    var actualSubmit = function() {
        // get formParams
        var formParams = job.get_form_params();

        if (formParams) { // if formParams is not null
            //submitJob(formParams);

            var url = pageInfo.basePath + "/oncoprint/oncoprintOut";
            //Set the results DIV to use the URL from the job.
            Ext.get('analysisOutput').load({url: url, callback: loadOncoprintOutput});
            //Set the flag that says we run an analysis so we can warn the user if they navigate away.
            GLOBAL.AnalysisRun = true;

            /*Ext.Ajax.request({
                url: pageInfo.basePath+"/oncoprint/getData",
                method: 'POST',
                success: function(result, request){
                    //Handle data export process
                    //runJob(result, formParams);
                    alert(result.responseText);
                },
                failure: function(result, request){
                    Ext.Msg.alert('Status', 'Unable to create data export job.');
                },
                timeout: '1800000',
                params: formParams
            });*/

        }
    }

    // Check whether we have the node details for the HD node already
    // If not, we should fetch them first
    if (typeof GLOBAL.HighDimDataType !== "undefined" && GLOBAL.HighDimDataType) {
        actualSubmit();
    } else {
        var divId = 'divIndependentVariable';
        runAllQueriesForSubsetId(function () {
            highDimensionalData.fetchNodeDetails(divId, function( result ) {
                highDimensionalData.data = JSON.parse(result.responseText);
                highDimensionalData.populate_data();
                actualSubmit();
            });
        }, divId);
    }
}

// get form params
OncoprintView.prototype.get_form_params = function () {
    var formParameters = {}; // init

    //Use a common function to load the High Dimensional Data params.
    loadCommonHighDimFormObjects(formParameters, "divIndependentVariable");

    // instantiate input elements object with their corresponding validations
    var inputArray = this.get_inputs(formParameters);

    // define the validator for this form
    var formValidator = new FormValidator(inputArray);

    if (formValidator.validateInputForm()) { // if input files satisfy the validations

        // get values
        var inputConceptPathVar = readConceptVariables("divIndependentVariable");

        // assign values to form parameters
        formParameters['jobType'] = 'Oncoprint';
        formParameters['independentVariable'] = inputConceptPathVar;
        formParameters['variablesConceptPaths'] = inputConceptPathVar;

        // get analysis constraints
        var constraints_json = this.get_analysis_constraints('Oncoprint');
        constraints_json['projections'] = ["zscore"];

        formParameters['analysisConstraints'] = JSON.stringify(constraints_json);

    } else { // something is not correct in the validation
        // empty form parameters
        formParameters = null;
        // display the error message
        formValidator.display_errors();
    }

    return formParameters;
}

OncoprintView.prototype.get_inputs = function (form_params) {
    return  [
        {
            "label" : "High Dimensional Data",
            "el" : Ext.get("divIndependentVariable"),
            "validations" : [
                {type:"REQUIRED"},
                {
                    type:"HIGH_DIMENSIONAL",
                    high_dimensional_type:form_params["divIndependentVariableType"],
                    high_dimensional_pathway:form_params["divIndependentVariablePathway"]
                }
            ]
        }
    ];
}

// init oncoprint view instance
var oncoprintView = new OncoprintView();


