package com.anymore.auto.gradle

import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue

class AutoServiceLogTest {

    @Test
    void 'INFO输出精确扫描摘要'() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream()
        AutoServiceLog log = new AutoServiceLog(Logger.INFO, 'debug', new PrintStream(bytes, true, 'UTF-8'))
        ServiceCatalog catalog = catalog()

        log.summary(catalog, 12L)

        assertEquals(
                '[auto-service] debug: scanned 2 unique classes, found 2 candidates, ' +
                        'registered 1 bindings across 1 interfaces, excluded 1, 12 ms.\n',
                bytes.toString('UTF-8'))
    }

    @Test
    void '没有注册项时输出可行动提示'() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream()
        AutoServiceLog log = new AutoServiceLog(Logger.INFO, 'release', new PrintStream(bytes, true, 'UTF-8'))

        log.summary(new ServiceCatalog(), 1L)

        String output = bytes.toString('UTF-8')
        assertTrue(output.contains('@AutoService'))
        assertTrue(output.contains('Application'))
        assertTrue(output.contains('auto-service'))
    }

    private static ServiceCatalog catalog() {
        ServiceCatalog catalog = new ServiceCatalog()
        ClassOrigin registered = new ClassOrigin('sample.Registered', 'classes', 'sample/Registered.class', 'a')
        ClassOrigin excluded = new ClassOrigin('sample.Excluded', 'classes', 'sample/Excluded.class', 'b')
        catalog.addClass(registered)
        catalog.addClass(excluded)
        catalog.addCandidate(new ServiceCandidate(
                'sample.Task', 'sample.Registered', 0, '', false,
                ServiceCandidateStatus.REGISTERED, null, registered))
        catalog.addCandidate(new ServiceCandidate(
                'sample.Task', 'sample.Excluded', 1, '', false,
                ServiceCandidateStatus.EXCLUDED, 'alias /debug/', excluded))
        catalog
    }
}
