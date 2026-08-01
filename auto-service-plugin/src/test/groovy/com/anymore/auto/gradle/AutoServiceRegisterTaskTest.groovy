package com.anymore.auto.gradle

import org.junit.Test
import org.gradle.api.GradleException

import static org.junit.Assert.assertEquals
import static org.junit.Assert.fail

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

    @Test
    void '排除规则数量不一致时给出明确错误'() {
        try {
            AutoServiceRegisterTask.toExclusiveRules(['example\\.legacy\\..*'], [])
            fail('规则数量不一致时应拒绝执行')
        } catch (GradleException exception) {
            assertEquals('排除规则的类名与别名数量不一致', exception.message)
        }
    }
}
