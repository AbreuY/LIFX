package de.jeisfeld.lifx.lan.message;

import java.net.DatagramPacket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.Map;

import de.jeisfeld.lifx.lan.util.TypeUtil;

/**
 * Response message of type StatePower.
 */
public class StatePower extends ResponseMessage {
	/**
	 * The power level.
	 */
	private short mLevel;

	/**
	 * Create a StatePower from message data.
	 *
	 * @param packet The message data.
	 */
	public StatePower(final DatagramPacket packet) {
		super(packet);
	}

	@Override
	public final MessageType getMessageType() {
		return MessageType.STATE_POWER;
	}

	@Override
	protected final void evaluatePayload() {
		ByteBuffer byteBuffer = ByteBuffer.wrap(getPayload());
		byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
		mLevel = byteBuffer.getShort();
	}

	@Override
	protected final Map<String, String> getPayloadMap() {
		Map<String, String> payloadFields = new LinkedHashMap<>();
		payloadFields.put("Level", TypeUtil.toUnsignedString(mLevel));
		return payloadFields;
	}

	/**
	 * Get the power level.
	 *
	 * @return the power level
	 */
	public short getLevel() {
		return mLevel;
	}
}
