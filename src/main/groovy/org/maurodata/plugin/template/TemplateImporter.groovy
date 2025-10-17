/**
 * Example Mauro Importer Plugin
 * -----------------------------
 *
 * This file provides an example Importer plugin for Mauro.
 * The importer implements 'DataModelImporterPlugin' - other interfaces for other model types are
 * available - e.g. 'TerminologyImporterPlugin', etc
 *
 * The generic parameter determines the parameters provided.  'FileImportParameters' provides the
 * default set of import options, plus a file to provide the model details.  You may choose to extend
 * this with additional import options, and use this as the generic parameter instead.  A method
 * `importParametersClass` returns this class.
 *
*/
package org.maurodata.plugin.template

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import jakarta.inject.Singleton
import org.maurodata.domain.datamodel.DataModel
import org.maurodata.plugin.importer.DataModelImporterPlugin
import org.maurodata.plugin.importer.FileImportParameters

@Slf4j
@Singleton
@CompileStatic
class TemplateImporter implements DataModelImporterPlugin<FileImportParameters>{

    // TODO: Every plugin should update this to match the required version number
    String version = '1.0.0'

    // TODO: Every plugin should update this to match the required plugin name
    String displayName = 'Template Import Plugin'

    // TODO: If you don't manually supply a namespace, then the package of this class will be used.
    // String namespace = 'org.maurodata.plugin.template'

    /**
     * Helper method to help determine whether this importer can handle a particular file.
     * TODO: Rewrite this to suit your application - for example, replace with something like:
     *   return contentType == 'application/xml'
     * for an importer that deals with xml files
     */
    @Override
    Boolean handlesContentType(String contentType) {
        return false
    }

    /**
     * Helper method to return the class of parameters this importer will handle.
     * TODO: Extend `FileImportParameters` or simply `ImportParameters` to include additional options.
     */
    @Override
    Class<FileImportParameters> importParametersClass() {
        return FileImportParameters
    }


    /**
     * TODO: This is the method that does all the work - implement this method.
     * Takes as input, a `params` object with all the parameters.
     * Returns a list of DataModel objects, which will be persisted by the framework.
     */
    @Override
    List<DataModel> importDomain(FileImportParameters params) {
        return []
    }


}
