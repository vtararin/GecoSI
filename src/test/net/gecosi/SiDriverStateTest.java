/**
 * Copyright (c) 2013 Simon Denier
 */
package test.net.gecosi;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.verify;

import java.util.concurrent.TimeoutException;

import net.gecosi.CommStatus;
import net.gecosi.CommWriter;
import net.gecosi.InvalidMessage;
import net.gecosi.SiDriverState;
import net.gecosi.SiHandler;
import net.gecosi.SiMessage;
import net.gecosi.SiMessageQueue;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * @author Simon Denier
 * @since Mar 15, 2013
 *
 */
public class SiDriverStateTest {

	private SiMessageQueue queue;

	@Mock
	private CommWriter writer;
	
	@Mock
	private SiHandler siHandler;

	@Before
	public void setUp() {
		MockitoAnnotations.initMocks(this);
		queue = new SiMessageQueue(5, 1);
	}
	
	@Test
	public void STARTUP_CHECK() throws Exception {
		queue.add(SiMessageFixtures.startup_answer);
		SiDriverState nextState = SiDriverState.STARTUP_CHECK.receive(queue, writer, siHandler);

		assertThat(nextState, equalTo(SiDriverState.EXTENDED_PROTOCOL_CHECK));
		verify(writer).write_debug(SiMessage.get_protocol_configuration);
	}

	@Test(expected=TimeoutException.class)
	public void STARTUP_CHECK_throwsTimeoutException() throws Exception {
		SiDriverState.STARTUP_CHECK.receive(queue, writer, siHandler);
	}

	@Test(expected=InvalidMessage.class)
	public void STARTUP_CHECK_throwsInvalidMessage() throws Exception {
		queue.add(SiMessage.ack_sequence);
		SiDriverState.STARTUP_CHECK.receive(queue, writer, siHandler);
	}

	@Test
	public void EXTENDED_PROTOCOL_CHECK() throws Exception {
		queue.add(SiMessageFixtures.config_answer);
		SiDriverState nextState = SiDriverState.EXTENDED_PROTOCOL_CHECK.receive(queue, writer, siHandler);

		assertThat(nextState, equalTo(SiDriverState.DISPATCH_READY));
	}

	@Test
	public void EXTENDED_PROTOCOL_CHECK_failsOnExtendedProtocolCheck() throws Exception {
		queue.add(SiMessageFixtures.no_ext_protocol_answer);
		SiDriverState nextState = SiDriverState.EXTENDED_PROTOCOL_CHECK.receive(queue, writer, siHandler);

		assertThat(nextState, equalTo(SiDriverState.EXTENDED_PROTOCOL_ERROR));
	}

	@Test
	public void DISPATCH_READY() throws Exception {
		queue.add(SiMessageFixtures.sicard5_detected);
		SiDriverState.DISPATCH_READY.receive(queue, writer, siHandler);
		verify(siHandler).notify(CommStatus.READY);
	}

	@Test
	public void DISPATCH_READY_dispatchesSiCard5() throws Exception {
		queue.add(SiMessageFixtures.sicard5_detected);
		SiDriverState nextState = SiDriverState.DISPATCH_READY.receive(queue, writer, siHandler);

		assertThat(nextState, equalTo(SiDriverState.WAIT_SICARD_5_DATA));
		verify(writer).write_debug(SiMessage.read_sicard_5);
	}
	
	@Test
	public void WAIT_SICARD_5_DATA() throws Exception {
		queue.add(SiMessageFixtures.sicard5_data);
		SiDriverState nextState = SiDriverState.WAIT_SICARD_5_DATA.receive(queue, writer, siHandler);

		verify(writer).write_debug(SiMessage.ack_sequence);
		assertThat(nextState, equalTo(SiDriverState.WAIT_SICARD_REMOVAL));
	}

	@Test
	public void WAIT_SICARD_5_DATA_removedFallbackToDispatchReady() throws Exception {
		queue.add(SiMessageFixtures.sicard5_removed);
		SiDriverState nextState = SiDriverState.WAIT_SICARD_5_DATA.receive(queue, writer, siHandler);

		verify(siHandler).notify(CommStatus.PROCESSING_ERROR);
		assertThat(nextState, equalTo(SiDriverState.DISPATCH_READY));
	}

	@Test
	public void WAIT_SICARD_5_DATA_timeoutFallbackToDispatchReady() throws Exception {
		SiDriverState nextState = SiDriverState.WAIT_SICARD_5_DATA.receive(queue, writer, siHandler);

		verify(siHandler).notify(CommStatus.PROCESSING_ERROR);
		assertThat(nextState, equalTo(SiDriverState.DISPATCH_READY));
	}

	@Test
	public void WAIT_SICARD_REMOVAL() throws Exception {
		queue.add(SiMessageFixtures.nak);
		queue.add(SiMessageFixtures.sicard5_removed);
		SiDriverState nextState = SiDriverState.WAIT_SICARD_REMOVAL.receive(queue, writer, siHandler);

		assertThat(nextState, equalTo(SiDriverState.DISPATCH_READY));
	}

	@Test
	public void WAIT_SICARD_REMOVAL_timeoutFallbackToDispatchReady() throws Exception {
		SiDriverState nextState = SiDriverState.WAIT_SICARD_REMOVAL.receive(queue, writer, siHandler);

		assertThat(nextState, equalTo(SiDriverState.DISPATCH_READY));
	}

}