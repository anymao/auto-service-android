package com.anymore.auto.gradle

import org.junit.Test

import static org.junit.Assert.assertEquals

class AutoServiceRegisterTaskTest {

    @Test
    void '排除规则按类名和别名配对并去重'() {
        def rules = AutoServiceRegisterTask.toExclusiveRules(
                ['example\\.legacy\\..*', '.*', 'example\\.legacy\\..*'],
                ['.*', 'debug-.*', '.*'])

        assertEquals([
                new ExclusiveRule('example\\.legacy\\..*', '.*'),
                new ExclusiveRule('.*', 'debug-.*')
        ] as Set, rules)
    }
}
