package com.anymore.auto.gradle

import org.junit.Test
import org.gradle.api.GradleException
import org.gradle.api.tasks.CacheableTask
import org.gradle.testfixtures.ProjectBuilder

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.fail

class AutoServiceRegisterTaskTest {

    @Test
    void '任务可缓存且诊断日志输入有稳定默认值'() {
        def task = ProjectBuilder.builder().build().tasks.create(
                'autoServiceTest', AutoServiceRegisterTask)

        assertNotNull(AutoServiceRegisterTask.getAnnotation(CacheableTask))
        assertFalse(task.diagnosticsEnabled.get())
        assertEquals(Logger.INFO, task.logLevel.get() as int)
        assertEquals('unknown', task.variantName.get())
    }

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
