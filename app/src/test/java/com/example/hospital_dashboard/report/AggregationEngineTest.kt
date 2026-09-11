package com.example.hospital_dashboard.report

import com.example.hospital_dashboard.report.engine.AggregationEngine
import com.example.hospital_dashboard.report.model.AggregationType
import com.example.hospital_dashboard.report.model.MetricDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AggregationEngineTest {

    @Test
    fun testBuildSelectExpressions_SumAndCount() {
        val metrics = listOf(
            MetricDefinition(id = "opd_cnt", name = "門診人次", sourceColumn = "opd_visit_count", aggregation = AggregationType.SUM),
            MetricDefinition(id = "record_cnt", name = "筆數", sourceColumn = "opd_visit_count", aggregation = AggregationType.COUNT)
        )
        val exprs = AggregationEngine.buildSelectExpressions(metrics)

        assertEquals(2, exprs.size)
        assertTrue(exprs[0].startsWith("COALESCE(SUM("))
        assertTrue(exprs[0].endsWith("AS opd_cnt"))
        assertEquals("COUNT(opd_visit_count) AS record_cnt", exprs[1])
    }

    @Test
    fun testCalculateDiffAndPercent() {
        val diff = AggregationEngine.calculateDiff(150.0, 100.0)
        assertEquals(50.0, diff, 0.0001)

        val pct = AggregationEngine.calculatePercentChange(150.0, 100.0)
        assertEquals(50.0, pct, 0.0001)

        val pctZeroPrev = AggregationEngine.calculatePercentChange(150.0, 0.0)
        assertEquals(0.0, pctZeroPrev, 0.0001)
    }
}
