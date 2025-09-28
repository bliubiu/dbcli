package com.dbcli.core;

import com.dbcli.config.AppConfig;
import com.dbcli.model.MetricResult;
import com.dbcli.service.ReportGenerator;
import com.dbcli.service.ReportGeneratorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DbCliRunnerTest {

    @Mock
    private AppConfig appConfig;

    @Mock
    private ReportGeneratorFactory reportGeneratorFactory;

    // We cannot use @InjectMocks directly because DbCliRunner creates its own dependencies.
    // We will manually instantiate it and set the factory for this test.
    private DbCliRunner dbCliRunner;

    // We need to use reflection to set the mocked factory into the runner
    // or change the runner to allow dependency injection. For now, reflection is quicker.
    // A better approach would be to refactor DbCliRunner to accept dependencies in constructor.
    // But let's stick to the plan. We will create a test-specific constructor later if needed.

    @BeforeEach
    void setUp() throws Exception {
        // Since DbCliRunner's constructor is complex, we'll mock its dependencies as needed.
        // For this specific test, we only care about the generateReports method.
        // We can use a real DbCliRunner and use reflection to replace its factory instance.
        dbCliRunner = new DbCliRunner(appConfig);

        // Use reflection to inject the mock factory
        java.lang.reflect.Field factoryField = DbCliRunner.class.getDeclaredField("reportGeneratorFactory");
        factoryField.setAccessible(true);
        factoryField.set(dbCliRunner, reportGeneratorFactory);
    }

    @Test
    void testGenerateReports_ShouldCallFactoryAndGenerators() throws Exception {
        // Arrange
        String format = "both";
        List<MetricResult> results = Collections.singletonList(new MetricResult());

        ReportGenerator mockExcelGenerator = mock(ReportGenerator.class);
        when(mockExcelGenerator.getFormat()).thenReturn("excel");

        ReportGenerator mockHtmlGenerator = mock(ReportGenerator.class);
        when(mockHtmlGenerator.getFormat()).thenReturn("html");

        List<ReportGenerator> mockGenerators = new ArrayList<>();
        mockGenerators.add(mockExcelGenerator);
        mockGenerators.add(mockHtmlGenerator);

        when(appConfig.getOutputFormat()).thenReturn(format);
        when(reportGeneratorFactory.createGenerators(format)).thenReturn(mockGenerators);

        // Act
        // The method is private, so we need to use reflection to call it.
        Method generateReportsMethod = DbCliRunner.class.getDeclaredMethod("generateReports", List.class);
        generateReportsMethod.setAccessible(true);
        generateReportsMethod.invoke(dbCliRunner, results);

        // Assert
        // Verify that the factory was called to create generators
        verify(reportGeneratorFactory, times(1)).createGenerators(format);

        // Verify that each generator's generate method was called exactly once
        verify(mockExcelGenerator, times(1)).generate(results, null, null);
        verify(mockHtmlGenerator, times(1)).generate(results, null, null);
    }
}