package com.recomdata.transmart.data.association

import com.google.common.collect.Maps
import grails.converters.JSON
import jobs.AnalysisConstraints
import jobs.UserParameters
import jobs.steps.OpenHighDimensionalDataStep
import org.springframework.beans.factory.annotation.Autowired
import org.transmartproject.core.dataquery.DataRow
import org.transmartproject.core.dataquery.highdim.AssayColumn
import org.transmartproject.core.dataquery.highdim.HighDimensionDataTypeResource
import org.transmartproject.core.dataquery.highdim.HighDimensionResource

class OncoprintController {

    @Autowired
    HighDimensionResource highDimensionResource


    def oncoprintOut = {
        render(template: "/plugin/oncoprint_out",
                model:[
                ],
                contextPath:pluginContextPath)
    }

    def fetchGeneData = {

        List<Map> oncoprintEntries = []

        UserParameters userParams = new UserParameters(map: Maps.newHashMap(params))
        AnalysisConstraints analysisConstraints = RModulesController.createAnalysisConstraints(params)
        HighDimensionDataTypeResource dataTypeResource = highDimensionResource.getSubResourceForType(analysisConstraints['data_type'])

        def openResultSetStep = new OpenHighDimensionalDataStep(
                params: userParams,
                dataTypeResource: dataTypeResource,
                analysisConstraints: analysisConstraints)
        openResultSetStep.execute()

        openResultSetStep.results.each() { key, tabularResult ->
            def assayList = tabularResult.indicesList
            tabularResult.each { DataRow row ->
                assayList.each { AssayColumn assayColumn ->
                    def value = row[assayColumn]
                    if (value == null) {
                        return
                    }

                    def oncoprintEntry = [
                            gene: row.geneSymbol,
                            sample: assayColumn.patientInTrialId
                    ]
                    if (value > 2.0) {
                        oncoprintEntry["mrna"] = "UPREGULATED"
                    }
                    if (value < -2.0) {
                        oncoprintEntry["mrna"] = "DOWNREGULATED"
                    }

                    oncoprintEntries << oncoprintEntry
                }
            }
            tabularResult.close()
        }
        render oncoprintEntries as JSON
    }

    def fetchClinicalAttributes = {
        render("[]")
    }

}

