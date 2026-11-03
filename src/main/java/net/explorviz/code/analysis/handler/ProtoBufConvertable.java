package net.explorviz.code.analysis.handler;

import com.google.protobuf.Message;

/**
 * Creates a Protobuffer data message from the object.
 *
 * @param <T> generated message type from protoBuffer
 */
public interface ProtoBufConvertable<T extends Message> {

  T getProtoBufObject();
}
