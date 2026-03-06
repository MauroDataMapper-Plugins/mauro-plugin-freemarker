package org.maurodata.plugin.freemarker.controllers

import org.maurodata.domain.diff.ObjectDiff
import org.maurodata.domain.model.AdministeredItem
import org.maurodata.domain.model.Model
import org.maurodata.domain.security.Role
import org.maurodata.persistence.model.AdministeredItemRepository
import org.maurodata.persistence.model.PathRepository
import org.maurodata.security.AccessControlService

import freemarker.core.OutputFormat
import freemarker.template.Template
import groovy.util.logging.Slf4j
import io.micronaut.core.annotation.NonNull
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Produces
import io.micronaut.http.exceptions.HttpStatusException
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import jakarta.inject.Inject

import freemarker.cache.StringTemplateLoader
import freemarker.template.Configuration

import io.micronaut.http.HttpResponse

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@Controller()
@Secured(SecurityRule.IS_AUTHENTICATED)
@Slf4j
class TemplateController {

    @Inject
    List<AdministeredItemRepository> administeredItemRepositories

    @Inject
    AccessControlService accessControlService

    @Inject
    PathRepository pathRepository

    @NonNull
    AdministeredItemRepository getAdministeredItemRepository(final String domainType) {

        administeredItemRepositories.find {
            final String simpleName = it.getClass().simpleName

            simpleName != 'AdministeredItemContentRepository' &&
            simpleName != 'ModelContentRepository' &&
            simpleName.toLowerCase().startsWith(domainType.toLowerCase())
        }
    }

    AdministeredItem getWithContents(final String domainType, final UUID itemId) {
        AdministeredItemRepository specificAdministeredItemRepository = getAdministeredItemRepository(domainType)
        if (specificAdministeredItemRepository == null) {
            throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "TMP01: No supporting service for domainType ${domainType}")
        }
        AdministeredItem itemWithContents = specificAdministeredItemRepository.loadWithContent(itemId)

        if (itemWithContents == null) {
            throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "TMP02: Cannot find item of type: ${domainType} with id: ${itemId}")
        }

        accessControlService.checkRole(Role.READER, itemWithContents)
        if (itemWithContents instanceof Model) {
            ((Model) itemWithContents).setAssociations()
        }

        pathRepository.readParentItems(itemWithContents)
        itemWithContents.updatePath()

        return itemWithContents
    }

    private static HttpResponse<byte[]> runTemplate(final String templateContents, final Map<String, Object> map) {

        final Configuration configuration = new Configuration(Configuration.VERSION_2_3_34)
        final StringTemplateLoader stringLoader = new StringTemplateLoader()
        stringLoader.putTemplate("exportTemplate", templateContents)
        configuration.setTemplateLoader(stringLoader)
        configuration.setTemplateUpdateDelayMilliseconds(0)
        Template template = configuration.getTemplate("exportTemplate")

        String[] names = template.getCustomAttributeNames()
        for (String name : names) {
            log.info("${name} ${template.getCustomAttribute(name).toString()}")
        }

        final Map<String, String> meta = [:]
        map.put("meta", meta)

        String characterEncoding = template.getEncoding()
        if (characterEncoding == null || characterEncoding.trim().isEmpty()) {
            characterEncoding = 'UTF-8'
        }
        meta.put("character-encoding", characterEncoding)

        String contentType
        String customContentType = template.getCustomAttribute("content_type")
        if (customContentType != null && !customContentType.trim().isEmpty()) {
            contentType = customContentType
        } else {
            final OutputFormat outputFormat = template.getOutputFormat()
            final String mimeType = outputFormat.getMimeType()
            if (mimeType != null) {
                contentType = mimeType
            } else {
                contentType = 'text/plain'
            }
        }

        meta.put("content-type", contentType)

        log.trace("runTemplate meta ${meta.toString()}")

        final ByteArrayOutputStream baos = new ByteArrayOutputStream(4096)
        try (Writer writer = new OutputStreamWriter(baos, characterEncoding)) {
            log.trace("Running template")
            template.process(map, writer)
            writer.flush()

            MutableHttpResponse<byte[]> response = HttpResponse.ok(baos.toByteArray())
            if (contentType != null) {
                log.trace("runTemplate contentType ${contentType}")
                response.contentType(contentType)
            } else {
                log.trace("runTemplate contentType TEXT_PLAIN_TYPE")
                response.contentType(MediaType.TEXT_PLAIN_TYPE)
            }

            if (characterEncoding != null) {
                log.trace("runTemplate characterEncoding ${characterEncoding}")
                response.characterEncoding(characterEncoding)
            } else {
                log.trace("runTemplate characterEncoding UTF_8")
                response.characterEncoding(StandardCharsets.UTF_8)
            }

            return response
        }
    }

    private static List<Path> detectResourcesDirectories() {
        URL url = TemplateController.class.getProtectionDomain().getCodeSource().getLocation()
        Path baseDirPath = Paths.get(url.toURI())

        final List<Path> resourcesDirectories = []

        // Application is in an IDE
        if (Files.isDirectory(baseDirPath)) {
            final Path aResourcesDirPath = findProjectRoot(baseDirPath)?.resolve("resources")
            if (aResourcesDirPath != null && Files.exists(aResourcesDirPath)) {resourcesDirectories << aResourcesDirPath}
        }

        // Application is in a packaged jar

        final Path aLocalResourcesDirPath = baseDirPath.getParent()?.resolve("resources")
        if (aLocalResourcesDirPath != null && Files.exists(aLocalResourcesDirPath)) {resourcesDirectories << aLocalResourcesDirPath}

        try {
            final Path aResourcesDirPath = findAppRoot(baseDirPath.getParent().getParent())?.resolve("resources")
            if (aResourcesDirPath != null && Files.exists(aResourcesDirPath)) {resourcesDirectories << aResourcesDirPath}
        }
        catch (NullPointerException ignore) {

        }

        log.debug("Resource directories: ${resourcesDirectories.toString()}")
        return resourcesDirectories
    }

    private static Path findProjectRoot(final Path start) {
        Path current = start
        while (current != null) {
            if (Files.exists(current.resolve("build.gradle")) ||
                Files.exists(current.resolve("pom.xml"))) {
                return current
            }
            current = current.getParent()
        }
        return null
    }

    private static Path findAppRoot(final Path start) {
        Path current = start
        while (current != null) {
            if (Files.exists(current.resolve("resources")) ||
                Files.exists(current.resolve("plugins"))
            ) {
                return current
            }
            current = current.getParent()
        }
        return null
    }

    private static String getTemplateContentsByName(final String name) {
        if (name == null || name.trim().isEmpty()) {throw new RuntimeException("TMP03.1: Missing name of resource")}
        // #CWE-33 Path Traversal
        if (name.indexOf('..') != -1) {throw new RuntimeException("TMP03.2: Malformed resource name")}

        // Look in resources/ directories first
        final List<Path> resourcesDirectories = detectResourcesDirectories()

        for (int r = 0; r < resourcesDirectories.size(); r++) {
            final Path resourceRelativeToResourcesDirectory = resourcesDirectories.get(r).resolve(name)
            if (Files.exists(resourceRelativeToResourcesDirectory)) {
                log.debug("getTemplateContentsByName ${resourceRelativeToResourcesDirectory.toString()}")
                return Files.readString(resourceRelativeToResourcesDirectory, StandardCharsets.UTF_8)
            }
        }

        // Via classpath
        final URL resource = TemplateController.class.classLoader.getResource(name)
        if (resource == null) {throw new RuntimeException("TMP03: Could not find ${name} on classpath")}

        try {
            if ("file" == resource.getProtocol()) {
                // E.g. build/resources
                Path path = Paths.get(resource.toURI())
                log.debug("getTemplateContentsByName ${path.toString()}")
                return Files.readString(path, StandardCharsets.UTF_8)
            } else {
                // Inside jar
                try (final InputStream is = TemplateController.class.classLoader.getResourceAsStream(name)) {
                    if (is == null) {throw new RuntimeException("TMP03: Could not load ${name} from classpath")}
                    log.debug("getTemplateContentsByName resource as stream: ${name}")
                    return new String(is.readAllBytes(), StandardCharsets.UTF_8)
                }
            }
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException("TMP03: Error loading ${name} from classpath: ${e.getMessage()}", e)
        }
    }

    @Post('/api/{domainType}/{itemId}/template')
    @Produces(MediaType.ALL)
    @Consumes(MediaType.ALL)
    HttpResponse<byte[]> template(final String domainType, final UUID itemId, @Body String templateContents) {

        final AdministeredItem rootItem = getWithContents(domainType, itemId)

        final Map<String, Object> map = new LinkedHashMap<>(3)

        map.put("domainType", domainType)
        map.put("id", itemId)
        map.put(domainType, rootItem)

        return runTemplate(templateContents, map)
    }

    @Get('/api/{domainType}/{itemId}/template{?name}')
    @Produces(MediaType.ALL)
    HttpResponse<byte[]> templateByName(final String domainType, final UUID itemId, final String name) {

        final String templateContents = getTemplateContentsByName(name)

        final AdministeredItem rootItem = getWithContents(domainType, itemId)

        final Map<String, Object> map = new LinkedHashMap<>(4)

        map.put("domainType", domainType)
        map.put("id", itemId)
        map.put("templateName", name)
        map.put(domainType, rootItem)

        return runTemplate(templateContents, map)
    }

    private HttpResponse<byte[]> runTemplateDiff(final String domainType, final UUID sourceId, final UUID targetId, final String templateContents, final String name) {
        final AdministeredItem sourceItem = getWithContents(domainType, sourceId)
        final AdministeredItem targetItem = getWithContents(domainType, targetId)

        if (!sourceItem instanceof Model) {
            throw new RuntimeException("TMP04: ${domainType} is not a Model sub-type: only Model sub-types can be diff-ed")
        }

        final Model sourceModel = (Model) sourceItem
        final Model targetModel = (Model) targetItem

        final ObjectDiff objectDiff = sourceModel.diff(targetModel)

        final Map<String, Object> map = new LinkedHashMap<>(6)

        map.put("domainType", domainType)
        map.put("sourceId", sourceId)
        map.put("targetId", targetId)
        map.put("sourceModel", sourceModel)
        map.put("targetModel", targetModel)
        map.put("diff", objectDiff)
        if (name != null) {map.put("templateName", name)}

        return runTemplate(templateContents, map)
    }

    @Post('/api/{domainType}/{sourceId}/diff/{targetId}/template')
    @Produces(MediaType.ALL)
    @Consumes(MediaType.ALL)
    HttpResponse<byte[]> templateDiff(final String domainType, final UUID sourceId, final UUID targetId, @Body String templateContents) {
        return runTemplateDiff(domainType, sourceId, targetId, templateContents, null)
    }

    @Get('/api/{domainType}/{sourceId}/diff/{targetId}/template{?name}')
    @Produces(MediaType.ALL)
    HttpResponse<byte[]> templateDiffByName(final String domainType, final UUID sourceId, final UUID targetId, final String name) {

        final String templateContents = getTemplateContentsByName(name)
        return runTemplateDiff(domainType, sourceId, targetId, templateContents, name)
    }
}
