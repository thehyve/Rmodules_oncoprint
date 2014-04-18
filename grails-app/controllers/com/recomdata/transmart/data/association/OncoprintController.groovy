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

        }
        render oncoprintEntries as JSON
    }

    def fetchClinicalAttributes = {
        render('''[{"attr_id":"AGILENT_EXPRESSION","display_name":"Agilent_expression","description":"Agilent_expression","datatype":"NUMBER"},{"attr_id":"ANATOMIC_ORGAN_SUBDIVISION","display_name":"anatomic_organ_subdivision","description":"anatomic_organ_subdivision","datatype":"STRING"},{"attr_id":"CANCER","display_name":"cancer","description":"cancer","datatype":"STRING"},{"attr_id":"COPY-NUMBER","display_name":"copy-number","description":"copy-number","datatype":"NUMBER"},{"attr_id":"DFS_MONTHS","display_name":"Disease Free (Months)","description":"Disease free in months since treatment","datatype":"NUMBER"},{"attr_id":"DFS_STATUS","display_name":"Disease Free Status","description":"Disease free status","datatype":"STRING"},{"attr_id":"EXPRESSION_SUBTYPE","display_name":"expression_subtype","description":"expression_subtype","datatype":"STRING"},{"attr_id":"GENDER","display_name":"Gender","description":"Gender","datatype":"STRING"},{"attr_id":"HISTOLOGY","display_name":"histology","description":"histology","datatype":"STRING"},{"attr_id":"HYPERMUTATED","display_name":"hypermutated","description":"hypermutated","datatype":"NUMBER"},{"attr_id":"ICLUSTER","display_name":"iCluster","description":"iCluster","datatype":"NUMBER"},{"attr_id":"METHYLATION","display_name":"methylation","description":"methylation","datatype":"NUMBER"},{"attr_id":"METHYLATION_SUBTYPE","display_name":"methylation_subtype","description":"methylation_subtype","datatype":"STRING"},{"attr_id":"MLH1_SILENCING","display_name":"MLH1 silencing","description":"Epigenetic silencing status of MLH1","datatype":"NUMBER"},{"attr_id":"MSI_STATUS","display_name":"MSI_status","description":"MSI_status","datatype":"STRING"},{"attr_id":"OS_MONTHS","display_name":"Overall Survival (Months)","description":"Overall survival in months since diagnosis","datatype":"NUMBER"},{"attr_id":"OS_STATUS","display_name":"Overall Survival Status","description":"Overall survival status","datatype":"STRING"},{"attr_id":"PRIMARY_TUMOR_PATHOLOGIC_SPREAD","display_name":"primary_tumor_pathologic_spread","description":"primary_tumor_pathologic_spread","datatype":"STRING"},{"attr_id":"SEQUENCED","display_name":"sequenced","description":"sequenced","datatype":"NUMBER"},{"attr_id":"TUMOR_SITE","display_name":"Site","description":"Site","datatype":"STRING"},{"attr_id":"TUMOR_STAGE_2009","display_name":"tumor_stage","description":"tumor_stage","datatype":"STRING"}]''')
    }

}

