package com.recomdata.transmart.data.association

import com.google.common.collect.Maps
import grails.converters.JSON
import jobs.AnalysisConstraints
import jobs.UserParameters
import jobs.steps.OpenHighDimensionalDataStep
import org.springframework.beans.factory.annotation.Autowired
import org.transmartproject.core.dataquery.DataRow
import org.transmartproject.core.dataquery.TabularResult
import org.transmartproject.core.dataquery.highdim.AssayColumn
import org.transmartproject.core.dataquery.highdim.HighDimensionDataTypeResource
import org.transmartproject.core.dataquery.highdim.HighDimensionResource
import org.transmartproject.core.dataquery.highdim.assayconstraints.AssayConstraint
import org.transmartproject.core.dataquery.highdim.dataconstraints.DataConstraint
import org.transmartproject.core.dataquery.highdim.projections.Projection

class OncoprintController {

    final static Map<String, String> projectionLookup = [
            "mrna":     "zscore",
            "acgh":     "acgh_values"
    ]

    /* in */
    UserParameters userParams
    HighDimensionDataTypeResource dataTypeResource
    AnalysisConstraints analysisConstraints

    /* out */
    Map<List<String>, TabularResult> results = [:]


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

        userParams = new UserParameters(map: Maps.newHashMap(params))
        analysisConstraints = RModulesController.createAnalysisConstraints(params)
        String dataType = analysisConstraints['data_type']
        dataTypeResource = highDimensionResource.getSubResourceForType(dataType)
        String projectionType = projectionLookup[dataType]

        try {
            List<String> ontologyTerms = extractOntologyTerms()
            extractPatientSets().eachWithIndex { resultInstanceId, index ->
                ontologyTerms.each { ontologyTerm ->
                    String seriesLabel = ontologyTerm.split('\\\\')[-1]
                    List<String> keyList = ["S" + (index + 1), seriesLabel]
                    results[keyList] = fetchSubset(resultInstanceId, ontologyTerm, projectionType)
                }
            }
        } catch(Throwable t) {
            results.values().each { it.close() }
            throw t
        }

        results.each() { key, tabularResult ->
            switch (dataType) {
                case 'mrna':
                    processMrna(tabularResult, oncoprintEntries)
                    break
                case 'acgh':
                    processAcgh(tabularResult, oncoprintEntries)
                    break
            }
            tabularResult.close()
        }
        render oncoprintEntries as JSON
    }

    private void processMrna(TabularResult tabularResult, List<Map> oncoprintEntries) {
        def assayList = tabularResult.indicesList
        tabularResult.each { DataRow row ->
            assayList.each { AssayColumn assayColumn ->
                def value = row[assayColumn] as Double
                if (value == null) {
                    return
                }

                def oncoprintEntry = [
                        gene: row.geneSymbol,
                        sample: assayColumn.patientInTrialId
                ]
                if (value > 1.0) {
                    oncoprintEntry["mrna"] = "UPREGULATED"
                }
                if (value < -1.0) {
                    oncoprintEntry["mrna"] = "DOWNREGULATED"
                }

                oncoprintEntries << oncoprintEntry
            }
        }
    }

    private void processAcgh(TabularResult tabularResult, List<Map> oncoprintEntries) {
        def assayList = tabularResult.indicesList
        tabularResult.each { DataRow row ->
            assayList.each { AssayColumn assayColumn ->
                def value = row[assayColumn].getCopyNumberState()
                if (value == null) {
                    return
                }

                def oncoprintEntry = [
                        gene: row.geneSymbol,
                        sample: assayColumn.patientInTrialId
                ]
                switch (value) {
                    case -1:
                        //TODO: not sure if this is accurate:
                        oncoprintEntry["cna"] = "HEMIZYGOUSLYDELETED"
                        break
                    case 0:
                        oncoprintEntry["cna"] = "DIPLOID"
                        break
                    case 1:
                        oncoprintEntry["cna"] = "GAINED"
                        break
                    case 2:
                        oncoprintEntry["cna"] = "AMPLIFIED"
                        break
                }

                oncoprintEntries << oncoprintEntry
            }
        }
    }

    def fetchClinicalAttributes = {
        render("[]")
    }


    private List<String> extractOntologyTerms() {
        analysisConstraints.assayConstraints.remove('ontology_term').collect {
            OpenHighDimensionalDataStep.createConceptKeyFrom(it.term)
        }
    }

    private List<Integer> extractPatientSets() {
        analysisConstraints.assayConstraints.remove("patient_set").grep()
    }

    private TabularResult fetchSubset(Integer patientSetId, String ontologyTerm, String projectionType) {

        List<DataConstraint> dataConstraints = analysisConstraints['dataConstraints'].
                collect { String constraintType, values ->
                    if (values) {
                        dataTypeResource.createDataConstraint(values, constraintType)
                    }
                }.grep()

        List<AssayConstraint> assayConstraints = analysisConstraints['assayConstraints'].
                collect { String constraintType, values ->
                    if (values) {
                        dataTypeResource.createAssayConstraint(values, constraintType)
                    }
                }.grep()

        assayConstraints.add(
                dataTypeResource.createAssayConstraint(
                        AssayConstraint.PATIENT_SET_CONSTRAINT,
                        result_instance_id: patientSetId))

        assayConstraints.add(
                dataTypeResource.createAssayConstraint(
                        AssayConstraint.ONTOLOGY_TERM_CONSTRAINT,
                        concept_key: ontologyTerm))

        Projection projection = dataTypeResource.createProjection([:], projectionType)

        dataTypeResource.retrieveData(assayConstraints, dataConstraints, projection)
    }

}

