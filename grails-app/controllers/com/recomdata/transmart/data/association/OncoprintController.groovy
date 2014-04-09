package com.recomdata.transmart.data.association

class OncoprintController {

    def RModulesOutputRenderService

    def oncoprintOut =
            {
                //This will be the array of image links.
                def ArrayList<String> imageLinks = new ArrayList<String>()

                //This will be the array of text file locations.
                def ArrayList<String> txtFiles = new ArrayList<String>()

                //Grab the job ID from the query string.
                String jobName = params.jobName

                //Gather the image links.
                RModulesOutputRenderService.initializeAttributes(jobName,"Oncoprint",imageLinks)

                String tempDirectory = RModulesOutputRenderService.tempDirectory

                //Traverse the temporary directory for the LinearRegression files.
                def tempDirectoryFile = new File(tempDirectory)

                render(template: "/plugin/oncoprint_out",
                        model:[
                                imageLocations:imageLinks,
                                zipLink:RModulesOutputRenderService.zipLink
                        ],
                        contextPath:pluginContextPath)

            }

}

