package com.epam.reportportal.testng.integration.util;

import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.restendpoint.http.MultiPartRequest;
import com.epam.reportportal.service.Launch;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.service.step.StepReporter;
import com.epam.reportportal.utils.properties.PropertiesLoader;
import com.epam.ta.reportportal.ws.model.BatchSaveOperatingRS;
import com.epam.ta.reportportal.ws.model.OperationCompletionRS;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.item.ItemCreatedRS;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRS;
import io.reactivex.Maybe;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import org.testng.ITestNGListener;
import org.testng.TestNG;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * @author <a href="mailto:ihar_kahadouski@epam.com">Ihar Kahadouski</a>
 */
public class TestUtils {

	public static final String TEST_NAME = "TestContainer";

	public static TestNG runTests(List<Class<? extends ITestNGListener>> listeners, Class<?>... classes) {
		final TestNG testNG = new TestNG(true);
		testNG.setListenerClasses(listeners);
		testNG.setTestClasses(classes);
		testNG.setDefaultTestName(TEST_NAME);
		testNG.setExcludedGroups("optional");
		testNG.run();
		return testNG;
	}

	public static Maybe<String> createMaybeUuid() {
		return createMaybe(UUID.randomUUID().toString());
	}

	public static <T> Maybe<T> createMaybe(T id) {
		return Maybe.create(emitter -> {
			emitter.onSuccess(id);
			emitter.onComplete();
		});
	}

	public static StartTestItemRQ extractRequest(ArgumentCaptor<StartTestItemRQ> captor, String methodType) {
		return captor.getAllValues()
				.stream()
				.filter(it -> methodType.equalsIgnoreCase(it.getType()))
				.findAny()
				.orElseThrow(() -> new AssertionError(String.format("Method type '%s' should be present among requests", methodType)));
	}

	public static List<StartTestItemRQ> extractRequests(ArgumentCaptor<StartTestItemRQ> captor, String methodType) {
		return captor.getAllValues().stream().filter(it -> methodType.equalsIgnoreCase(it.getType())).collect(Collectors.toList());
	}

	/**
	 * Generates a unique ID shorter than UUID based on current time in milliseconds and thread ID.
	 *
	 * @return a unique ID string
	 */
	public static String generateUniqueId() {
		return System.currentTimeMillis() + "-" + Thread.currentThread().getId() + "-" + ThreadLocalRandom.current().nextInt(9999);
	}

	public static StartTestItemRQ standardStartStepRequest() {
		StartTestItemRQ rq = new StartTestItemRQ();
		rq.setStartTime(Calendar.getInstance().getTime());
		String id = generateUniqueId();
		rq.setName("Step_" + id);
		rq.setDescription("Test step description");
		rq.setUniqueId(id);
		rq.setType("STEP");
		return rq;
	}

	public static void mockLaunch(Launch launch, StepReporter reporter, Maybe<String> launchUuid, Maybe<String> suiteUuid,
			Maybe<String> testClassUuid, Collection<Maybe<String>> testMethodUuidList) {
		mockLaunch(launch,
				new ListenerParameters(PropertiesLoader.load()),
				reporter,
				launchUuid,
				suiteUuid,
				testClassUuid,
				testMethodUuidList
		);
	}

	@SuppressWarnings("unchecked")
	public static void mockLaunch(Launch launch, ListenerParameters parameters, StepReporter reporter, Maybe<String> launchUuid,
			Maybe<String> suiteUuid, Maybe<String> testClassUuid, Collection<Maybe<String>> testMethodUuidList) {
		when(launch.getParameters()).thenReturn(parameters);
		when(launch.getStepReporter()).thenReturn(reporter);

		when(launch.start()).thenReturn(launchUuid);
		when(launch.startTestItem(any())).thenReturn(suiteUuid);
		when(launch.startTestItem(same(suiteUuid), any())).thenReturn(testClassUuid);

		Iterator<Maybe<String>> methodIterator = testMethodUuidList.iterator();
		Maybe<String> first = methodIterator.next();
		List<Maybe<String>> methodMaybes = new ArrayList<>();
		methodIterator.forEachRemaining(methodMaybes::add);
		Maybe<String>[] other = methodMaybes.toArray(new Maybe[0]);
		when(launch.startTestItem(same(testClassUuid), any())).thenReturn(first, other);

		new HashSet<>(testMethodUuidList).forEach(methodUuidMaybe -> when(launch.finishTestItem(same(methodUuidMaybe), any())).thenReturn(
				TestUtils.createMaybe(new OperationCompletionRS())));
		when(launch.finishTestItem(same(testClassUuid), any())).thenReturn(TestUtils.createMaybe(new OperationCompletionRS()));
		when(launch.finishTestItem(same(suiteUuid), any())).thenReturn(TestUtils.createMaybe(new OperationCompletionRS()));
	}

	public static void mockLaunch(ReportPortalClient client, String launchUuid, String suiteUuid, String testClassUuid,
			String testMethodUuid) {
		mockLaunch(client, launchUuid, suiteUuid, testClassUuid, Collections.singleton(testMethodUuid));
	}

	public static String namedUuid(String name) {
		return name + UUID.randomUUID().toString().substring(name.length());
	}

	@SuppressWarnings("unchecked")
	public static void mockLaunch(ReportPortalClient client, String launchUuid, String suiteUuid, String testClassUuid,
			Collection<String> testMethodUuidList) {
		when(client.startLaunch(any())).thenReturn(TestUtils.createMaybe(new StartLaunchRS(launchUuid, 1L)));

		Maybe<ItemCreatedRS> suiteMaybe = TestUtils.createMaybe(new ItemCreatedRS(suiteUuid, suiteUuid));
		when(client.startTestItem(any())).thenReturn(suiteMaybe);

		Maybe<ItemCreatedRS> testClassMaybe = TestUtils.createMaybe(new ItemCreatedRS(testClassUuid, testClassUuid));
		when(client.startTestItem(eq(suiteUuid), any())).thenReturn(testClassMaybe);

		List<Maybe<ItemCreatedRS>> responses = testMethodUuidList.stream()
				.map(uuid -> TestUtils.createMaybe(new ItemCreatedRS(uuid, uuid)))
				.collect(Collectors.toList());
		Maybe<ItemCreatedRS> first = responses.get(0);
		Maybe<ItemCreatedRS>[] other = responses.subList(1, responses.size()).toArray(new Maybe[0]);
		when(client.startTestItem(eq(testClassUuid), any())).thenReturn(first, other);
		new HashSet<>(testMethodUuidList).forEach(testMethodUuid -> when(client.finishTestItem(eq(testMethodUuid), any())).thenReturn(
				TestUtils.createMaybe(new OperationCompletionRS())));

		Maybe<OperationCompletionRS> testClassFinishMaybe = TestUtils.createMaybe(new OperationCompletionRS());
		when(client.finishTestItem(eq(testClassUuid), any())).thenReturn(testClassFinishMaybe);

		Maybe<OperationCompletionRS> suiteFinishMaybe = TestUtils.createMaybe(new OperationCompletionRS());
		when(client.finishTestItem(eq(suiteUuid), any())).thenReturn(suiteFinishMaybe);

		when(client.finishLaunch(eq(launchUuid), any())).thenReturn(TestUtils.createMaybe(new OperationCompletionRS()));

		when(client.log(any(MultiPartRequest.class))).thenReturn(TestUtils.createMaybe(new BatchSaveOperatingRS()));
	}

	public static List<String> mockNestedSteps(ReportPortalClient client, String testMethodUuid) {
		List<String> nestedStepsUuids = new CopyOnWriteArrayList<>();
		Supplier<Maybe<ItemCreatedRS>> maybeSupplier = () -> {
			String uuid = UUID.randomUUID().toString();
			nestedStepsUuids.add(uuid);
			return TestUtils.createMaybe(new ItemCreatedRS(uuid, uuid));
		};

		when(client.startTestItem(eq(testMethodUuid), any())).thenAnswer((Answer<Maybe<ItemCreatedRS>>) invocation -> maybeSupplier.get());
		when(client.finishTestItem(eq(testMethodUuid),
				any()
		)).thenAnswer((Answer<Maybe<OperationCompletionRS>>) invocation -> TestUtils.createMaybe(new OperationCompletionRS()));
		return nestedStepsUuids;
	}
}
