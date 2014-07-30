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

class GeneprintController {

    final static Map<String, String> projectionLookup = [
            "mrna":     "zscore",
            "acgh":     "acgh_values",
            "protein":  "zscore"
    ]

    AnalysisConstraints analysisConstraints

    List<Map> geneprintEntries = []

    @Autowired
    HighDimensionResource highDimensionResource

    def geneprintOut = {
        render(template: "/plugin/geneprint_out",
                model:[
                ],
                contextPath:pluginContextPath)
    }

    def fetchGeneData = {
        analysisConstraints = RModulesController.createAnalysisConstraints(params)

        List<String> ontologyTerms = extractOntologyTerms()
        List<Integer> searchKeywordIds = extractSearchKeywordIds()
        TabularResult tabularResult;

        Map<String, HighDimensionDataTypeResource> highDimDataTypeResourceCache = [:]
        ontologyTerms.each { ontologyTerm ->
            highDimDataTypeResourceCache[ontologyTerm] = getHighDimDataTypeResourceFromConcept(ontologyTerm)
        }

        try {
            extractPatientSets().each { resultInstanceId ->
                searchKeywordIds.each { searchKeywordId ->
                    def searchKeyword = [symbol: null]
                    highDimDataTypeResourceCache.each { ontologyTerm, dataTypeResource ->
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

        render geneprintEntries as JSON
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

                def geneprintEntry = getOrCreateGeneprintEntry(searchKeyword.symbol,
                        assayColumn.patientInTrialId)

                def value = row[assayColumn]
                if (value == null) {
                    return
                }

                switch (dataType) {
                case 'mrna':
                    processMrna(value, geneprintEntry)
                    break
                case 'acgh':
                    processAcgh(value.getCopyNumberState(), geneprintEntry)
                    break
                case 'protein':
                    processProtein(value, geneprintEntry)
                    break
                }
            }
        }
    }

    private void processMrna(Double value, geneprintEntry) {
        if (value > 1.0) {
            geneprintEntry["mrna"] = "UPREGULATED"
        }
        if (value < -1.0) {
            geneprintEntry["mrna"] = "DOWNREGULATED"
        }
    }

    private void processAcgh(CopyNumberState value, geneprintEntry) {
        switch (value) {
        case CopyNumberState.LOSS:
            geneprintEntry["cna"] = "LOSS"
            break
        case CopyNumberState.NORMAL:
            geneprintEntry["cna"] = "DIPLOID"
            break
        case CopyNumberState.GAIN:
            geneprintEntry["cna"] = "GAINED"
            break
        case CopyNumberState.AMPLIFICATION:
            geneprintEntry["cna"] = "AMPLIFIED"
            break
        }
    }

    private void processProtein(Double value, geneprintEntry) {
        if (value > 1.0) {
            geneprintEntry["rppa"] = "UPREGULATED"
        }
        if (value < -1.0) {
            geneprintEntry["rppa"] = "DOWNREGULATED"
        }
    }

    private Map getOrCreateGeneprintEntry(String geneSymbol, String sampleId) {
        Map entry = geneprintEntries.find( {
            it.gene == geneSymbol && it.sample == sampleId
        })
        if (entry != null) {
            return entry
        }
        entry = [ gene: geneSymbol, sample: sampleId ]
        geneprintEntries << entry
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
        analysisConstraints.dataConstraints.remove('search_keyword_ids').keyword_ids
    }

    private List<Integer> extractPatientSets() {
        analysisConstraints.assayConstraints.remove("patient_set").grep()
    }

    private TabularResult fetchData(Integer patientSetId, String searchKeywordId, String ontologyTerm,
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

