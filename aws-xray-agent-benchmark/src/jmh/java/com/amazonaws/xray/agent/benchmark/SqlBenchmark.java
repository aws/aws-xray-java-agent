package com.amazonaws.xray.agent.benchmark;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.agent.benchmark.source.StatementImpl;
import com.amazonaws.xray.agent.utils.BenchmarkUtils;
import com.amazonaws.xray.sql.TracingStatement;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.sql.SQLException;
import java.sql.Statement;

public class SqlBenchmark {
    @State(Scope.Benchmark)
    public static class BenchmarkState {
        Statement statement;

        @Setup(Level.Trial)
        public void setup() {
            BenchmarkUtils.configureXRayRecorder();

            if (System.getProperty("com.amazonaws.xray.sdk") != null) {
                statement = TracingStatement.decorateStatement(new StatementImpl());
            } else {
                statement = new StatementImpl();
            }
        }
    }

    @Benchmark
    public void sqlQuery(BenchmarkState state) throws SQLException {
        AWSXRay.beginSegment("Benchmark");
        state.statement.executeQuery("SQL");
        AWSXRay.endSegment();
    }
}
