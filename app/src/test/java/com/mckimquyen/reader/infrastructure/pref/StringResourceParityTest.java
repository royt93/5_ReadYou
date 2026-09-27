package com.mckimquyen.reader.infrastructure.pref;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Guards against the exact class of bug fixed in FIX-10 (i18n resource qualifier mismatch):
 * every `values-&lt;qualifier&gt;/strings.xml` on disk must declare the SAME set of
 * {@code &lt;string name="..."&gt;} keys as the English base ({@code values/strings.xml}) — no
 * missing keys (a translation gap that silently falls back to English at runtime) and no
 * duplicate keys within one file (a copy/agent error that XML would otherwise accept silently).
 * <p>
 * This is a plain JVM/file-based test (no Robolectric, no Android framework) because it is
 * purely a static check of resource XML files under {@code app/src/main/res/}; it runs against
 * the actual source tree so it catches drift the moment a translation file falls behind, not just
 * at APK-build time.
 */
public class StringResourceParityTest {

    private static final File RES_DIR = new File("src/main/res");
    private static final File BASE_STRINGS = new File(RES_DIR, "values/strings.xml");

    /**
     * The 6 locales Gradle's {@code resourceConfigurations} originally shipped before FIX-10, and
     * that CLAUDE.md still calls out as "the" supported set for new UI text — kept as a floor so a
     * future edit can't silently drop one of these without a test noticing, even though the app
     * now ships every locale under {@code res/values-*}.
     */
    private static final String[] CORE_LOCALE_DIRS = {
            "values-vi", "values-zh-rCN", "values-ja", "values-fr-rFR", "values-de-rDE"
    };

    private Set<String> extractKeys(File stringsXml) throws IOException, ParserConfigurationException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(stringsXml);
        NodeList nodes = doc.getElementsByTagName("string");
        Set<String> keys = new TreeSet<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Element el = (Element) nodes.item(i);
            String name = el.getAttribute("name");
            assertTrue(
                    stringsXml + ": found a <string> with a blank name attribute",
                    name != null && !name.trim().isEmpty()
            );
            keys.add(name);
        }
        return keys;
    }

    /** Every non-"values" (i.e. locale-qualified) `values-*` directory holding a strings.xml. */
    private File[] localeStringsFiles() {
        File[] dirs = RES_DIR.listFiles(f -> f.isDirectory() && f.getName().startsWith("values-"));
        assertTrue("Expected to find values-* locale directories under " + RES_DIR, dirs != null && dirs.length > 0);
        Map<String, File> files = new LinkedHashMap<>();
        for (File dir : dirs) {
            File strings = new File(dir, "strings.xml");
            // values-night/ only overrides colors.xml/themes.xml for dark theme, not a locale.
            if (strings.exists()) {
                files.put(dir.getName(), strings);
            }
        }
        return files.values().toArray(new File[0]);
    }

    @Test
    public void baseStringsXml_exists_andIsNonEmpty() throws Exception {
        assertTrue("Base app/src/main/res/values/strings.xml must exist", BASE_STRINGS.exists());
        Set<String> baseKeys = extractKeys(BASE_STRINGS);
        assertTrue("Base strings.xml must declare a substantial number of keys", baseKeys.size() > 100);
    }

    @Test
    public void everyLocale_hasNoDuplicateKeys() throws Exception {
        StringBuilder failures = new StringBuilder();
        for (File localeFile : localeStringsFiles()) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            Document doc = factory.newDocumentBuilder().parse(localeFile);
            NodeList nodes = doc.getElementsByTagName("string");
            Set<String> seen = new HashSet<>();
            Set<String> dupes = new TreeSet<>();
            for (int i = 0; i < nodes.getLength(); i++) {
                String name = ((Element) nodes.item(i)).getAttribute("name");
                if (!seen.add(name)) {
                    dupes.add(name);
                }
            }
            if (!dupes.isEmpty()) {
                failures.append(localeFile).append(": duplicate keys ").append(dupes).append("\n");
            }
        }
        if (failures.length() > 0) {
            fail("Duplicate <string name=\"...\"> entries found:\n" + failures);
        }
    }

    /**
     * The bug this guards: a translation file missing keys the base declares means those UI
     * strings silently fall back to English for that locale's users at runtime — exactly what
     * happened to 32 locales before FIX-10 (each missing the same 100 keys added across several
     * feature commits without ever touching those translation files).
     */
    @Test
    public void everyLocale_hasNoMissingKeysComparedToBase() throws Exception {
        Set<String> baseKeys = extractKeys(BASE_STRINGS);
        StringBuilder failures = new StringBuilder();
        for (File localeFile : localeStringsFiles()) {
            Set<String> localeKeys = extractKeys(localeFile);
            Set<String> missing = new TreeSet<>(baseKeys);
            missing.removeAll(localeKeys);
            if (!missing.isEmpty()) {
                failures.append(localeFile)
                        .append(": missing ").append(missing.size()).append(" keys, e.g. ")
                        .append(missing.iterator().next()).append("\n");
            }
        }
        if (failures.length() > 0) {
            fail("Locale files missing keys present in the English base:\n" + failures);
        }
    }

    /**
     * The inverse of the missing-key check: a locale file must not declare keys the base doesn't
     * have — that's either a typo'd key name (silently dead, never read at runtime because the
     * app always looks resources up by the base's key) or leftover cruft from a removed feature.
     */
    @Test
    public void everyLocale_hasNoExtraKeysNotInBase() throws Exception {
        Set<String> baseKeys = extractKeys(BASE_STRINGS);
        StringBuilder failures = new StringBuilder();
        for (File localeFile : localeStringsFiles()) {
            Set<String> localeKeys = extractKeys(localeFile);
            Set<String> extra = new TreeSet<>(localeKeys);
            extra.removeAll(baseKeys);
            if (!extra.isEmpty()) {
                failures.append(localeFile)
                        .append(": extra keys not in base, e.g. ").append(extra.iterator().next())
                        .append(" (").append(extra.size()).append(" total)\n");
            }
        }
        if (failures.length() > 0) {
            fail("Locale files declare keys the English base does not have (typo or dead cruft):\n" + failures);
        }
    }

    @Test
    public void coreLocales_declareExactlyTheSameKeyCountAsBase() throws Exception {
        int baseCount = extractKeys(BASE_STRINGS).size();
        for (String dirName : CORE_LOCALE_DIRS) {
            File f = new File(RES_DIR, dirName + "/strings.xml");
            assertTrue("Expected core locale file to exist: " + f, f.exists());
            int count = extractKeys(f).size();
            assertEquals(dirName + " key count must match the English base exactly", baseCount, count);
        }
    }

    /**
     * The other half of FIX-10: `resourceConfigurations` in app/build.gradle must list a
     * qualifier that exactly matches each res/values-<qualifier>/ directory name. A qualifier
     * that's "close" (e.g. 'fr' when the directory is 'values-fr-rFR') silently drops that
     * locale's translations from the built APK even though the source file itself is complete —
     * this is a static parse of build.gradle, not a full Gradle evaluation, but it directly
     * re-creates the mismatch this task fixed.
     */
    @Test
    public void resourceConfigurations_matchesEveryLocaleDirectoryExactly() throws Exception {
        File buildGradle = new File("build.gradle");
        assertTrue("Expected to find app/build.gradle at " + buildGradle.getAbsolutePath(), buildGradle.exists());
        String content = new String(java.nio.file.Files.readAllBytes(buildGradle.toPath()), java.nio.charset.StandardCharsets.UTF_8);

        java.util.regex.Matcher blockMatcher = java.util.regex.Pattern
                .compile("resourceConfigurations\\s*\\+=\\s*\\[(.*?)\\]", java.util.regex.Pattern.DOTALL)
                .matcher(content);
        assertTrue("Expected a resourceConfigurations += [...] block in app/build.gradle", blockMatcher.find());

        Set<String> declaredQualifiers = new TreeSet<>();
        java.util.regex.Matcher entryMatcher = java.util.regex.Pattern.compile("'([^']+)'").matcher(blockMatcher.group(1));
        while (entryMatcher.find()) {
            declaredQualifiers.add(entryMatcher.group(1));
        }
        assertTrue("Failed to parse any locale entries out of resourceConfigurations", declaredQualifiers.size() > 5);

        Set<String> actualLocaleDirs = new TreeSet<>();
        for (File localeFile : localeStringsFiles()) {
            String dirName = localeFile.getParentFile().getName(); // e.g. "values-fr-rFR"
            actualLocaleDirs.add(dirName.substring("values-".length()));
        }

        Set<String> missingFromGradle = new TreeSet<>(actualLocaleDirs);
        missingFromGradle.removeAll(declaredQualifiers);
        assertTrue(
                "These res/values-* locales exist on disk but are NOT listed in app/build.gradle's "
                        + "resourceConfigurations, so their translations are silently stripped from every "
                        + "built APK: " + missingFromGradle,
                missingFromGradle.isEmpty()
        );

        Set<String> staleInGradle = new TreeSet<>(declaredQualifiers);
        staleInGradle.remove("en"); // "en" has no values-en/ directory; it IS the default values/.
        staleInGradle.removeAll(actualLocaleDirs);
        assertTrue(
                "These qualifiers are listed in resourceConfigurations but match no res/values-* "
                        + "directory on disk (dead entry, or a typo'd qualifier silently dropping the "
                        + "locale it meant to reference): " + staleInGradle,
                staleInGradle.isEmpty()
        );
    }
}
