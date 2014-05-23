package com.recomdata.transmart.data.association

import grails.converters.JSON
import jobs.AnalysisConstraints
import jobs.steps.OpenHighDimensionalDataStep
import org.springframework.beans.factory.annotation.Autowired
import org.transmartproject.core.dataquery.DataRow
import org.transmartproject.core.dataquery.TabularResult
import org.transmartproject.core.dataquery.highdim.AssayColumn
import org.transmartproject.core.dataquery.highdim.HighDimensionDataTypeResource
import org.transmartproject.core.dataquery.highdim.HighDimensionResource
import org.transmartproject.core.dataquery.highdim.acgh.CopyNumberState
import org.transmartproject.core.dataquery.highdim.assayconstraints.AssayConstraint
import org.transmartproject.core.dataquery.highdim.dataconstraints.DataConstraint
import org.transmartproject.core.dataquery.highdim.projections.Projection

class OncoprintController {

    final static Map<String, String> projectionLookup = [
            "mrna":     "zscore",
            "acgh":     "acgh_values",
            "protein":  "zscore"
    ]

    AnalysisConstraints analysisConstraints

    List<Map> oncoprintEntries = []

    @Autowired
    HighDimensionResource highDimensionResource

    def oncoprintOut = {
        render(template: "/plugin/oncoprint_out",
                model:[
                ],
                contextPath:pluginContextPath)
    }

    def fetchGeneData = {
        analysisConstraints = RModulesController.createAnalysisConstraints(params)

        List<String> ontologyTerms = extractOntologyTerms()
        List<Integer> searchKeywordIds = extractSearchKeywordIds()
        TabularResult tabularResult;

        try {
            extractPatientSets().each { resultInstanceId ->
                searchKeywordIds.each { searchKeywordId ->
                    def searchKeyword = [symbol: null]
                    ontologyTerms.each { ontologyTerm ->
                        HighDimensionDataTypeResource dataTypeResource = getHighDimDataTypeResourceFromConcept(ontologyTerm)
                        tabularResult = fetchData(resultInstanceId, searchKeywordId, ontologyTerm, dataTypeResource)
                        processResult(tabularResult, searchKeyword, dataTypeResource.dataTypeName)
                        tabularResult.close()
                    }
                }
            }
        } catch(Throwable t) {
            tabularResult?.close()
            throw t
        }

        render oncoprintEntries as JSON
    }

    def fetchClinicalAttributes = {
        render("[]")
    }

    private void processResult(TabularResult tabularResult, searchKeyword, String dataType) {
        def assayList = tabularResult.indicesList
        tabularResult.each { DataRow row ->
            if (searchKeyword.symbol == null) {
                searchKeyword.symbol = row.bioMarker
            }
            assayList.each { AssayColumn assayColumn ->

                def oncoprintEntry = getOrCreateOncoprintEntry(searchKeyword.symbol,
                        assayColumn.patientInTrialId)

                def value = row[assayColumn]
                if (value == null) {
                    return
                }

                switch (dataType) {
                case 'mrna':
                    processMrna(value, oncoprintEntry)
                    break
                case 'acgh':
                    processAcgh(value.getCopyNumberState(), oncoprintEntry)
                    break
                case 'protein':
                    processProtein(value, oncoprintEntry)
                    break
                }
            }
        }
    }

    private void processMrna(Double value, oncoprintEntry) {
        if (value > 1.0) {
            oncoprintEntry["mrna"] = "UPREGULATED"
        }
        if (value < -1.0) {
            oncoprintEntry["mrna"] = "DOWNREGULATED"
        }
    }

    private void processAcgh(CopyNumberState value, oncoprintEntry) {
        switch (value) {
        case CopyNumberState.LOSS:
            oncoprintEntry["cna"] = "LOSS"
            break
        case CopyNumberState.NORMAL:
            oncoprintEntry["cna"] = "DIPLOID"
            break
        case CopyNumberState.GAIN:
            oncoprintEntry["cna"] = "GAINED"
            break
        case CopyNumberState.AMPLIFICATION:
            oncoprintEntry["cna"] = "AMPLIFIED"
            break
        }
    }

    private void processProtein(Double value, oncoprintEntry) {
        if (value > 1.0) {
            oncoprintEntry["rppa"] = "UPREGULATED"
        }
        if (value < -1.0) {
            oncoprintEntry["rppa"] = "DOWNREGULATED"
        }
    }

    private Map getOrCreateOncoprintEntry(String geneSymbol, String sampleId) {
        Map entry = oncoprintEntries.find( {
            it.gene == geneSymbol && it.sample == sampleId
        })
        if (entry != null) {
            return entry
        }
        entry = [ gene: geneSymbol, sample: sampleId ]
        oncoprintEntries << entry
        return entry
    }

    private HighDimensionDataTypeResource getHighDimDataTypeResourceFromConcept(String conceptKey) {
        def constraints = []

        constraints << highDimensionResource.createAssayConstraint(
                AssayConstraint.DISJUNCTION_CONSTRAINT,
                subconstraints: [
                        (AssayConstraint.ONTOLOGY_TERM_CONSTRAINT):
                                [concept_key: conceptKey]])

        def assayMultiMap = highDimensionResource.
                getSubResourcesAssayMultiMap(constraints)

        HighDimensionDataTypeResource dataTypeResource = assayMultiMap.keySet()[0]
        return dataTypeResource
    }

    private List<String> extractOntologyTerms() {
        analysisConstraints.assayConstraints.remove('ontology_term').collect {
            OpenHighDimensionalDataStep.createConceptKeyFrom(it.term)
        }
    }

    private List<Integer> extractSearchKeywordIds() {
        analysisConstraints.dataConstraints.remove('search_keyword_ids').keyword_ids.collect {
            it.value
        }
    }

    private List<Integer> extractPatientSets() {
        analysisConstraints.assayConstraints.remove("patient_set").grep()
    }

    private TabularResult fetchData(Integer patientSetId, Integer searchKeywordId, String ontologyTerm,
                                    HighDimensionDataTypeResource dataTypeResource) {

        List<DataConstraint> dataConstraints = analysisConstraints['dataConstraints'].
                collect { String constraintType, values ->
                    if (values) {
                        dataTypeResource.createDataConstraint(values, constraintType)
                    }
                }.grep()

        dataConstraints.add(
                dataTypeResource.createDataConstraint(
                        DataConstraint.SEARCH_KEYWORD_IDS_CONSTRAINT,
                        keyword_ids: [searchKeywordId]
                )
        )

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

        String projectionType = projectionLookup[dataTypeResource.dataTypeName]
        Projection projection = dataTypeResource.createProjection([:], projectionType)

        dataTypeResource.retrieveData(assayConstraints, dataConstraints, projection)
    }

}

