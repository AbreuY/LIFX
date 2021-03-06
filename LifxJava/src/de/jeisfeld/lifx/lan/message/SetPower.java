package de.jeisfeld.lifx.lan.message;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import de.jeisfeld.lifx.lan.type.Power;

/**
 * Request message of type SetPower.
 */
public class SetPower extends RequestMessage {
	/**
	 * The status.
	 */
	private final boolean mStatus;

	/**
	 * Create SetPower.
	 *
	 * @param status The target power status.
	 */
	public SetPower(final boolean status) {
		mStatus = status;
	}

	@Override
	protected final byte[] getPayload() {
		ByteBuffer byteBuffer = ByteBuffer.allocate(2);
		byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
		byteBuffer.putShort((mStatus ? Power.ON : Power.OFF).getLevel());
		return byteBuffer.array();
	}

	@Override
	protected final MessageType getMessageType() {
		return MessageType.SET_POWER;
	}

	@Override
	protected final MessageType getResponseType() {
		return MessageType.ACKNOWLEDGEMENT;
	}

}
