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

    /* in */
    UserParameters userParams
    AnalysisConstraints analysisConstraints

    /* out */
    Map<List<String>, TabularResult> results = [:]
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

        userParams = new UserParameters(map: Maps.newHashMap(params))
        analysisConstraints = RModulesController.createAnalysisConstraints(params)

        List<String> ontologyTerms = extractOntologyTerms()
        TabularResult tabularResult;

        try {
            extractPatientSets().eachWithIndex { resultInstanceId, index ->
                ontologyTerms.each { ontologyTerm ->
                    HighDimensionDataTypeResource dataTypeResource = getHighDimDataTypeResourceFromConcept(ontologyTerm)
                    tabularResult = fetchData(resultInstanceId, ontologyTerm, dataTypeResource)

                    switch (dataTypeResource.dataTypeName) {
                    case 'mrna':
                        processMrna(tabularResult, oncoprintEntries)
                        break
                    case 'acgh':
                        processAcgh(tabularResult, oncoprintEntries)
                        break
                    case 'protein':
                        processProtein(tabularResult, oncoprintEntries)
                        break
                    }
                    tabularResult.close()
                }
            }
        } catch(Throwable t) {
            tabularResult?.close()
            throw t
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

                def oncoprintEntry = getOrCreateOncoprintEntry(row.bioMarker,
                        assayColumn.patientInTrialId)
                if (value > 1.0) {
                    oncoprintEntry["mrna"] = "UPREGULATED"
                }
                if (value < -1.0) {
                    oncoprintEntry["mrna"] = "DOWNREGULATED"
                }
            }
        }
    }

    private Map getOrCreateOncoprintEntry(String geneSymbol, String sampleId) {
        Map entry = oncoprintEntries.find( {
            it.gene == geneSymbol && it.sample == sampleId
        })
        if (entry != null)
            return entry
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

    private void processAcgh(TabularResult tabularResult, List<Map> oncoprintEntries) {
        def assayList = tabularResult.indicesList
        tabularResult.each { DataRow row ->
            assayList.each { AssayColumn assayColumn ->
                def value = row[assayColumn].getCopyNumberState()
                if (value == null) {
                    return
                }

                def oncoprintEntry = getOrCreateOncoprintEntry(row.bioMarker,
                        assayColumn.patientInTrialId)
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
        }
    }

    private void processProtein(TabularResult tabularResult, List<Map> oncoprintEntries) {
        def assayList = tabularResult.indicesList
        tabularResult.each { DataRow row ->
            assayList.each { AssayColumn assayColumn ->
                def value = row[assayColumn] as Double
                if (value == null) {
                    return
                }

                def oncoprintEntry = getOrCreateOncoprintEntry(row.bioMarker,
                        assayColumn.patientInTrialId)
                if (value > 1.0) {
                    oncoprintEntry["rppa"] = "UPREGULATED"
                }
                if (value < -1.0) {
                    oncoprintEntry["rppa"] = "DOWNREGULATED"
                }
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

    private TabularResult fetchData(Integer patientSetId, String ontologyTerm,
                                      HighDimensionDataTypeResource dataTypeResource) {

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

        String projectionType = projectionLookup[dataTypeResource.dataTypeName]
        Projection projection = dataTypeResource.createProjection([:], projectionType)

        dataTypeResource.retrieveData(assayConstraints, dataConstraints, projection)
    }

}

