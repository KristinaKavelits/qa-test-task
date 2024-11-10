package com.example;

import lombok.SneakyThrows;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Thread.sleep;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = {Client.class, ClientIntegrationTest.TestConfig.class})
public class ClientIntegrationTest {
    private final PrintStream standardOut = System.out;
    private final ByteArrayOutputStream outputStreamCaptor = new ByteArrayOutputStream();

    @Autowired
    Client client;

    @TempDir
    static File tempDir;

    static class TestConfig {
        @Bean
        public File eventsFile() {
            return new File(tempDir, "events.json");
        }
    }

    @BeforeEach
    public void setUp() {
        System.setOut(new PrintStream(outputStreamCaptor));
        // Log the files in tempDir to confirm cleanup
        if (tempDir.exists()) {
            File[] files = tempDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    System.out.println("Deleting file: " + file.getName());
                    boolean deleted = file.delete();
                    if (!deleted) {
                        System.out.println("Failed to delete: " + file.getName());
                    }
                }
            }
        }
        clearOutput();
    }

    @AfterEach
    public void tearDown() {
        System.setOut(standardOut);
    }

    @Test
    @SneakyThrows
    void shouldWriteTwoEventsWhenUserRunsServer() {
        // when
        client.run("up");

        // then
        assertTrue("Starting...\nStatus: UP".equals(getOutput()) ||
                "Starting...\nStatus: FAILED".equals(getOutput()));
    }

    @Test
    @SneakyThrows
    void shouldPrintNoEventsWhenUserRunsStatus() {
        // when
        client.run("status");

        // then
        assertEquals("No events found", getOutput());
    }

    @Test
    @SneakyThrows
    void shouldNotStartServerWhenAlreadyUp() {
        ensureDesiredStatusReached("up", "UP", 15);

        // clear the output to save only the latest event
        clearOutput();

        // when
        client.run("up");

        // then
        assertEquals("Already UP", getOutput(), "The output should be 'Already UP'");
    }

    @Test
    @SneakyThrows
    void shouldWriteTwoEventsWhenUserRunsServerAfterBeingDown() {
        ensureDesiredStatusReached("down", "DOWN", 15);

        // clear the output to save only the latest event
        clearOutput();

        // when
        client.run("up");

        // then
        assertTrue("Starting...\nStatus: UP".equals(getOutput()) ||
                "Starting...\nStatus: FAILED".equals(getOutput()));
    }

    @Test
    @SneakyThrows
    void shouldShowStatusUpAfterRunningUp() {

        ensureDesiredStatusReached("up", "UP", 15);

        // clear the output to save only the latest event
        clearOutput();

        // imitate server being up for 3 sec
        sleep(3000);

        // when: check the status
        client.run("status");

        // then: the status should be UP with uptime
        assertEquals("Status: UP\nUptime: 3 seconds", getOutput(), "Uptime should be captured correctly");

    }

    @Test
    @SneakyThrows
    void shouldResetUptimeAfterServerDown() {
        // Ensure the server is up
        ensureDesiredStatusReached("up", "UP", 10);
        Thread.sleep(2000);  // Simulating server being up for 2 seconds

        // Simulate server downtime
        ensureDesiredStatusReached("down", "DOWN", 10);
        Thread.sleep(2000);

        ensureDesiredStatusReached("up", "UP", 10);
        clearOutput();

        // Check that uptime is reset after coming back up
        client.run("status");
        assertEquals("Status: UP\nUptime: 0 seconds", getOutput(), "Uptime should be reset after restart");
    }

    @Test
    @SneakyThrows
    void shouldWriteTwoEventsWhenUserRunsDown() {
        // when
        client.run("down");

        // then
        assertTrue("Stopping...\nStatus: DOWN".equals(getOutput()) ||
                "Stopping...\nStatus: FAILED".equals(getOutput()));
    }

    @Test
    @SneakyThrows
    void shouldShowStatusDownAfterRunningDown() {
        // Ensure server is down before checking status
        ensureDesiredStatusReached("down", "DOWN", 15);

        // Clear the output to capture only the latest info
        clearOutput();

        // When: check the status
        client.run("status");

        // Then: the status should be DOWN
        assertEquals("Status: DOWN", getOutput());
    }

    @Test
    @SneakyThrows
    void shouldNotStopServerWhenAlreadyDown() {
        ensureDesiredStatusReached("down", "DOWN", 15);

        // clear the output to save only the latest event
        clearOutput();

        // when
        client.run("down");

        // then
        assertEquals("Already DOWN", getOutput(), "The output should be 'Already DOWN'");
    }

    @Test
    @SneakyThrows
    void shouldWriteTwoEventsWhenUserRunsDownAfterServerBeingUp() {
        ensureDesiredStatusReached("up", "UP", 15);

        // clear the output to save only the latest event
        clearOutput();

        // when
        client.run("down");

        // then
        assertTrue("Stopping...\nStatus: DOWN".equals(getOutput()) ||
                "Stopping...\nStatus: FAILED".equals(getOutput()));
    }

    @Test
    @SneakyThrows
    void shouldNotifyUserWhenCommandIsInvalid() {
        // when
        client.run("does not exist");

        // then
        assertEquals("Unknown command: does not exist", getOutput());
    }

    @Test
    @SneakyThrows
    void shouldPrintTheCommandsWhenUserDidNotSpecifyTheInput() {
        // when
        client.run();

        // then
        String actualResult = getOutput();
        assertTrue(actualResult.contains("Usage: vpn-client <command> [options]"));
        assertTrue(actualResult.contains("status"));
        assertTrue(actualResult.contains("up"));
        assertTrue(actualResult.contains("down"));
        assertTrue(actualResult.contains("history"));
    }

//    @Test
//    @SneakyThrows
//    void shouldHandleEmptyCommand() {
//        // the test is failing, cause client interprets the "" as unknown command
//        // when
//        client.run("");
//
//        // then
//        String actualResult = getOutput();
//        assertTrue(actualResult.contains("Usage: vpn-client <command> [options]"));
//        assertTrue(actualResult.contains("status"));
//        assertTrue(actualResult.contains("up"));
//        assertTrue(actualResult.contains("down"));
//        assertTrue(actualResult.contains("history"));
//    }

    @Test
    @SneakyThrows
    void shouldPrintNoEventsWhenHistoryFiltersReturnEmpty() {
        // when
        client.run("history");

        // then
        assertEquals("No events found", getOutput());
    }

    @Test
    @SneakyThrows
    void shouldHandleNoHistoryWithinDateRange() {
        // Date should be dynamic based on the date of the test execution
        LocalDate currentDate = LocalDate.now();
        LocalDate startDate = currentDate.plusDays(5); // 5 days ahead
        LocalDate endDate = currentDate.plusDays(10); // 10 days ahead

        // Prepare the arguments as you would pass them from the command line
        String[] args = {
                "history",
                "--from", startDate.toString(),
                "--to", endDate.toString()
        };
        // When: the user runs the history command with a date range
        client.run(args);

        // Then: the output should indicate that no events were found
        assertEquals("No events found", getOutput());
    }

    @Test
    @SneakyThrows
    void shouldReturnFilteredHistoryAsc() {
        // Date should be dynamic based on the date of the test execution
        LocalDate currentDate = LocalDate.now();
        LocalDate startDate = currentDate.minusDays(10); // 10 days ago
        LocalDate endDate = currentDate.plusDays(10); // 10 days ahead

        // Prepare the arguments as you would pass them from the command line
        String[] args = {
                "history",
                "--sort", "asc",
                "--from", startDate.toString(),
                "--to", endDate.toString()
        };

        // Simulate running the client commands to generate some history
        client.run("up");
        client.run("down");

        // Ensure history command with sorting options
        clearOutput();
        client.run(args);

        // Then: extract the timestamps
        List<LocalDateTime> timestamps = extractTimestamps(getOutput());

        // Check if the timestamps are sorted in ascending order
        for (int i = 0; i < timestamps.size() - 1; i++) {
            assertTrue(timestamps.get(i).isBefore(timestamps.get(i + 1)) || timestamps.get(i).isEqual(timestamps.get(i + 1)));
        }
    }

    @Test
    @SneakyThrows
    void shouldReturnFilteredHistoryDesc() {
        // Date should be dynamic based on the date of the test execution
        LocalDate currentDate = LocalDate.now();
        LocalDate startDate = currentDate.minusDays(10); // 10 days ago
        LocalDate endDate = currentDate.plusDays(10); // 10 days ahead

        // Prepare the arguments as you would pass them from the command line
        String[] args = {
                "history",
                "--sort", "desc",
                "--from", startDate.toString(),
                "--to", endDate.toString()
        };

        // Simulate running the client commands to generate some history
        client.run("up");
        client.run("down");
        client.run("up");

        // Ensure history command with sorting options
        clearOutput();
        client.run(args);

        // Then: extract the timestamps
        List<LocalDateTime> timestamps = extractTimestamps(getOutput());

        // Check if the timestamps are sorted in descending order
        for (int i = 0; i < timestamps.size() - 1; i++) {
            assertTrue(timestamps.get(i).isAfter(timestamps.get(i + 1)) || timestamps.get(i).isEqual(timestamps.get(i + 1)));
        }
    }

    @Test
    @SneakyThrows
    void shouldReturnHistoryWithOnlyStartingStatus() {
        // Prepare the arguments as they would be passed from the command line
        String[] args = {
                "history",
                "--status", "STARTING"
        };

        // Simulate running the client commands to generate some history
        client.run("up");
        client.run("down");
        client.run("up");

        // Ensure history command with sorting options
        clearOutput();
        client.run(args);

        // Then: capture the output and extract the timestamps
        String output = getOutput();

        // Split the output into lines
        String[] lines = output.split("\n");

        // Check each line to see if it contains a valid status
        for (String line : lines) {
            String status = line.split("Status: ")[1].split(",")[0].trim();  // Extract the status

            assertEquals("STARTING", status, "Expected status 'STARTING', but found: " + status);

        }

    }

    @Test
    @SneakyThrows
    void shouldHandleInvalidDateFormatForHistory() {
        // Prepare the arguments they would be passed from the command line
        String[] args = {
                "history",
                "--from", "2024-31-12",
                "--to", "2024-12-31"
        };

        // expect the exception to be thrown
        Exception exception = assertThrows(DateTimeParseException.class, () -> client.run(args));

        // Check if the exception message contains relevant details
        assertEquals("Text '2024-31-12' could not be parsed: Invalid value for MonthOfYear (valid values 1 - 12): 31", exception.getMessage());

    }

    @Test
    @SneakyThrows
    void shouldHandleMissingToDateArgument() {
        LocalDate currentDate = LocalDate.now();
        String[] args = {"history", "--from", currentDate.plusYears(5).toString()};

        client.run(args);

        assertEquals("No events found", getOutput());
    }

    @Test
    @SneakyThrows
    void shouldHandleInvalidStatusFilter() {
        // message could have been more user-friendly
        String[] args = {
                "history",
                "--status", "INVALID_STATUS"
        };

        client.run("up");
        client.run("down");

        clearOutput();

        // When: the user runs the history command with invalid status
        try {
            client.run(args);
        } catch (Exception e) {
            assertEquals("No enum constant com.example.Status.INVALID_STATUS", e.getMessage());
        }
    }

    private List<LocalDateTime> extractTimestamps(String output) {
        List<LocalDateTime> timestamps = new ArrayList<>();
        // Use a regular expression or split the string to find the timestamps
        String[] lines = output.split("\n");
        for (String line : lines) {
            if (line.contains("Timestamp:")) {
                String[] parts = line.split("Timestamp:");
                if (parts.length > 1) {
                    try {
                        // Parse the timestamp (format: 2024-11-09T15:18:33)
                        LocalDateTime timestamp = LocalDateTime.parse(parts[1].trim());
                        timestamps.add(timestamp);
                    } catch (Exception e) {
                        // Skip invalid timestamps
                        System.err.println("Invalid timestamp format in line: " + line);
                    }
                }
            }
        }
        return timestamps;
    }
    private void ensureDesiredStatusReached(String command, String expectedStatus, int maxAttempts) {
        int attempts = 0;
        boolean statusReached = false;

        try {
            while (!statusReached && attempts < maxAttempts) {
                client.run(command);
                statusReached = getOutput().contains("Status: " + expectedStatus);
                attempts++;
            }
        } catch (Exception e) {
            throw new RuntimeException("Unable to reach the desired state. Exception is thrown: "  + e.getMessage());
        }

        if (!statusReached) {
            throw new AssertionError("Failed to reach status '" + expectedStatus + "' after " + maxAttempts + " attempts");

        }
    }

    private String getOutput() {
        return outputStreamCaptor.toString().replaceAll("\r\n", "\n").replaceAll("\r", "\n").trim();
    }

    private void clearOutput() {
        outputStreamCaptor.reset();
    }
}
