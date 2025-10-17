/**
 * Example Mauro Exporter Plugin
 * -----------------------------
 *
 * This file provides an example Exporter plugin for Mauro.
 * The exporter implements 'DataModelExporterPlugin' - other interfaces for other model types are
 * available - e.g. 'TerminologyExporterPlugin', etc
 *
 * This is intended to take one or more models and return a file - in this case represented by a
 * byte array of contents, a file name, and a file extension.  The 'content type' is provided for the API to
 * provide back to the client.
*/
package org.maurodata.plugin.template

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import jakarta.inject.Singleton
import org.maurodata.domain.datamodel.DataModel
import org.maurodata.plugin.exporter.DataModelExporterPlugin
import org.maurodata.plugin.importer.DataModelImporterPlugin
import org.maurodata.plugin.importer.FileImportParameters

@Slf4j
@Singleton
@CompileStatic
class TemplateExporter implements DataModelExporterPlugin {

    // TODO: Every plugin should update this to match the required version number
    String version = '1.0.0'

    // TODO: Every plugin should update this to match the required plugin name
    String displayName = 'Template Import Plugin'

    // TODO: If you don't manually supply a namespace, then the package of this class will be used.
    // String namespace = 'org.maurodata.plugin.template'


    // TODO: Override this if this exporter plugin is capable of exporting multiple models into a single file
    // @Override
    // Boolean getCanExportMultipleDomains() {
    //    false
    // }

    /**
     * TODO: Implement this method to provide the file extension for any downloadable files.
     * For example, return `xls' for an Excel spreadsheet
     * @return
     */
    @Override
    String getFileExtension() {
        return ''
    }

    /**
     * TODO: Implement this method to provide the file content type downloadable files.
     * For example, return `application/xml' for an XML document
     * @return
     */
    @Override
    String getContentType() {
        return null
    }

    /**
     * TODO: Implement this method to provide the file name for a downloadable file.
     * This is typically related to the name of the dataModel - e.g. model.label
     * @return
     */
    @Override
    String getFileName(DataModel model) {
        return null
    }

    /**
     * TODO: Implement these two methods to actually do the work of taking a data model and returning some file contents
     * Two methods are available - one for working with a single model, another for dealing with a collection of them.  You can
     * share implementation by calling one from the other.
     * @return
     */
    @Override
    byte[] exportModel(DataModel model) {
        return null
    }

    @Override
    byte[] exportModels(Collection<DataModel> models) {
        return null
    }



}
