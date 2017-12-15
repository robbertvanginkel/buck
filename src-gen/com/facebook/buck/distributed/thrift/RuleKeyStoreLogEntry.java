/**
 * Autogenerated by Thrift Compiler (0.9.3)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package com.facebook.buck.distributed.thrift;

import org.apache.thrift.scheme.IScheme;
import org.apache.thrift.scheme.SchemeFactory;
import org.apache.thrift.scheme.StandardScheme;

import org.apache.thrift.scheme.TupleScheme;
import org.apache.thrift.protocol.TTupleProtocol;
import org.apache.thrift.protocol.TProtocolException;
import org.apache.thrift.EncodingUtils;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.server.AbstractNonblockingServer.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.EnumMap;
import java.util.Set;
import java.util.HashSet;
import java.util.EnumSet;
import java.util.Collections;
import java.util.BitSet;
import java.nio.ByteBuffer;
import java.util.Arrays;
import javax.annotation.Generated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings({"cast", "rawtypes", "serial", "unchecked"})
@Generated(value = "Autogenerated by Thrift Compiler (0.9.3)", date = "2017-12-03")
public class RuleKeyStoreLogEntry implements org.apache.thrift.TBase<RuleKeyStoreLogEntry, RuleKeyStoreLogEntry._Fields>, java.io.Serializable, Cloneable, Comparable<RuleKeyStoreLogEntry> {
  private static final org.apache.thrift.protocol.TStruct STRUCT_DESC = new org.apache.thrift.protocol.TStruct("RuleKeyStoreLogEntry");

  private static final org.apache.thrift.protocol.TField STORE_ID_FIELD_DESC = new org.apache.thrift.protocol.TField("storeId", org.apache.thrift.protocol.TType.STRING, (short)1);
  private static final org.apache.thrift.protocol.TField STORE_TTLSECONDS_FIELD_DESC = new org.apache.thrift.protocol.TField("storeTTLSeconds", org.apache.thrift.protocol.TType.I64, (short)2);
  private static final org.apache.thrift.protocol.TField LAST_STORE_EPOCH_SECONDS_FIELD_DESC = new org.apache.thrift.protocol.TField("lastStoreEpochSeconds", org.apache.thrift.protocol.TType.I64, (short)3);
  private static final org.apache.thrift.protocol.TField LAST_ATTEMPTED_STORE_EPOCH_SECONDS_FIELD_DESC = new org.apache.thrift.protocol.TField("lastAttemptedStoreEpochSeconds", org.apache.thrift.protocol.TType.I64, (short)4);
  private static final org.apache.thrift.protocol.TField LAST_FETCH_EPOCH_SECONDS_FIELD_DESC = new org.apache.thrift.protocol.TField("lastFetchEpochSeconds", org.apache.thrift.protocol.TType.I64, (short)5);

  private static final Map<Class<? extends IScheme>, SchemeFactory> schemes = new HashMap<Class<? extends IScheme>, SchemeFactory>();
  static {
    schemes.put(StandardScheme.class, new RuleKeyStoreLogEntryStandardSchemeFactory());
    schemes.put(TupleScheme.class, new RuleKeyStoreLogEntryTupleSchemeFactory());
  }

  public String storeId; // optional
  public long storeTTLSeconds; // optional
  public long lastStoreEpochSeconds; // optional
  public long lastAttemptedStoreEpochSeconds; // optional
  public long lastFetchEpochSeconds; // optional

  /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
  public enum _Fields implements org.apache.thrift.TFieldIdEnum {
    STORE_ID((short)1, "storeId"),
    STORE_TTLSECONDS((short)2, "storeTTLSeconds"),
    LAST_STORE_EPOCH_SECONDS((short)3, "lastStoreEpochSeconds"),
    LAST_ATTEMPTED_STORE_EPOCH_SECONDS((short)4, "lastAttemptedStoreEpochSeconds"),
    LAST_FETCH_EPOCH_SECONDS((short)5, "lastFetchEpochSeconds");

    private static final Map<String, _Fields> byName = new HashMap<String, _Fields>();

    static {
      for (_Fields field : EnumSet.allOf(_Fields.class)) {
        byName.put(field.getFieldName(), field);
      }
    }

    /**
     * Find the _Fields constant that matches fieldId, or null if its not found.
     */
    public static _Fields findByThriftId(int fieldId) {
      switch(fieldId) {
        case 1: // STORE_ID
          return STORE_ID;
        case 2: // STORE_TTLSECONDS
          return STORE_TTLSECONDS;
        case 3: // LAST_STORE_EPOCH_SECONDS
          return LAST_STORE_EPOCH_SECONDS;
        case 4: // LAST_ATTEMPTED_STORE_EPOCH_SECONDS
          return LAST_ATTEMPTED_STORE_EPOCH_SECONDS;
        case 5: // LAST_FETCH_EPOCH_SECONDS
          return LAST_FETCH_EPOCH_SECONDS;
        default:
          return null;
      }
    }

    /**
     * Find the _Fields constant that matches fieldId, throwing an exception
     * if it is not found.
     */
    public static _Fields findByThriftIdOrThrow(int fieldId) {
      _Fields fields = findByThriftId(fieldId);
      if (fields == null) throw new IllegalArgumentException("Field " + fieldId + " doesn't exist!");
      return fields;
    }

    /**
     * Find the _Fields constant that matches name, or null if its not found.
     */
    public static _Fields findByName(String name) {
      return byName.get(name);
    }

    private final short _thriftId;
    private final String _fieldName;

    _Fields(short thriftId, String fieldName) {
      _thriftId = thriftId;
      _fieldName = fieldName;
    }

    public short getThriftFieldId() {
      return _thriftId;
    }

    public String getFieldName() {
      return _fieldName;
    }
  }

  // isset id assignments
  private static final int __STORETTLSECONDS_ISSET_ID = 0;
  private static final int __LASTSTOREEPOCHSECONDS_ISSET_ID = 1;
  private static final int __LASTATTEMPTEDSTOREEPOCHSECONDS_ISSET_ID = 2;
  private static final int __LASTFETCHEPOCHSECONDS_ISSET_ID = 3;
  private byte __isset_bitfield = 0;
  private static final _Fields optionals[] = {_Fields.STORE_ID,_Fields.STORE_TTLSECONDS,_Fields.LAST_STORE_EPOCH_SECONDS,_Fields.LAST_ATTEMPTED_STORE_EPOCH_SECONDS,_Fields.LAST_FETCH_EPOCH_SECONDS};
  public static final Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
  static {
    Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap = new EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
    tmpMap.put(_Fields.STORE_ID, new org.apache.thrift.meta_data.FieldMetaData("storeId", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING)));
    tmpMap.put(_Fields.STORE_TTLSECONDS, new org.apache.thrift.meta_data.FieldMetaData("storeTTLSeconds", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I64)));
    tmpMap.put(_Fields.LAST_STORE_EPOCH_SECONDS, new org.apache.thrift.meta_data.FieldMetaData("lastStoreEpochSeconds", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I64)));
    tmpMap.put(_Fields.LAST_ATTEMPTED_STORE_EPOCH_SECONDS, new org.apache.thrift.meta_data.FieldMetaData("lastAttemptedStoreEpochSeconds", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I64)));
    tmpMap.put(_Fields.LAST_FETCH_EPOCH_SECONDS, new org.apache.thrift.meta_data.FieldMetaData("lastFetchEpochSeconds", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I64)));
    metaDataMap = Collections.unmodifiableMap(tmpMap);
    org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(RuleKeyStoreLogEntry.class, metaDataMap);
  }

  public RuleKeyStoreLogEntry() {
  }

  /**
   * Performs a deep copy on <i>other</i>.
   */
  public RuleKeyStoreLogEntry(RuleKeyStoreLogEntry other) {
    __isset_bitfield = other.__isset_bitfield;
    if (other.isSetStoreId()) {
      this.storeId = other.storeId;
    }
    this.storeTTLSeconds = other.storeTTLSeconds;
    this.lastStoreEpochSeconds = other.lastStoreEpochSeconds;
    this.lastAttemptedStoreEpochSeconds = other.lastAttemptedStoreEpochSeconds;
    this.lastFetchEpochSeconds = other.lastFetchEpochSeconds;
  }

  public RuleKeyStoreLogEntry deepCopy() {
    return new RuleKeyStoreLogEntry(this);
  }

  @Override
  public void clear() {
    this.storeId = null;
    setStoreTTLSecondsIsSet(false);
    this.storeTTLSeconds = 0;
    setLastStoreEpochSecondsIsSet(false);
    this.lastStoreEpochSeconds = 0;
    setLastAttemptedStoreEpochSecondsIsSet(false);
    this.lastAttemptedStoreEpochSeconds = 0;
    setLastFetchEpochSecondsIsSet(false);
    this.lastFetchEpochSeconds = 0;
  }

  public String getStoreId() {
    return this.storeId;
  }

  public RuleKeyStoreLogEntry setStoreId(String storeId) {
    this.storeId = storeId;
    return this;
  }

  public void unsetStoreId() {
    this.storeId = null;
  }

  /** Returns true if field storeId is set (has been assigned a value) and false otherwise */
  public boolean isSetStoreId() {
    return this.storeId != null;
  }

  public void setStoreIdIsSet(boolean value) {
    if (!value) {
      this.storeId = null;
    }
  }

  public long getStoreTTLSeconds() {
    return this.storeTTLSeconds;
  }

  public RuleKeyStoreLogEntry setStoreTTLSeconds(long storeTTLSeconds) {
    this.storeTTLSeconds = storeTTLSeconds;
    setStoreTTLSecondsIsSet(true);
    return this;
  }

  public void unsetStoreTTLSeconds() {
    __isset_bitfield = EncodingUtils.clearBit(__isset_bitfield, __STORETTLSECONDS_ISSET_ID);
  }

  /** Returns true if field storeTTLSeconds is set (has been assigned a value) and false otherwise */
  public boolean isSetStoreTTLSeconds() {
    return EncodingUtils.testBit(__isset_bitfield, __STORETTLSECONDS_ISSET_ID);
  }

  public void setStoreTTLSecondsIsSet(boolean value) {
    __isset_bitfield = EncodingUtils.setBit(__isset_bitfield, __STORETTLSECONDS_ISSET_ID, value);
  }

  public long getLastStoreEpochSeconds() {
    return this.lastStoreEpochSeconds;
  }

  public RuleKeyStoreLogEntry setLastStoreEpochSeconds(long lastStoreEpochSeconds) {
    this.lastStoreEpochSeconds = lastStoreEpochSeconds;
    setLastStoreEpochSecondsIsSet(true);
    return this;
  }

  public void unsetLastStoreEpochSeconds() {
    __isset_bitfield = EncodingUtils.clearBit(__isset_bitfield, __LASTSTOREEPOCHSECONDS_ISSET_ID);
  }

  /** Returns true if field lastStoreEpochSeconds is set (has been assigned a value) and false otherwise */
  public boolean isSetLastStoreEpochSeconds() {
    return EncodingUtils.testBit(__isset_bitfield, __LASTSTOREEPOCHSECONDS_ISSET_ID);
  }

  public void setLastStoreEpochSecondsIsSet(boolean value) {
    __isset_bitfield = EncodingUtils.setBit(__isset_bitfield, __LASTSTOREEPOCHSECONDS_ISSET_ID, value);
  }

  public long getLastAttemptedStoreEpochSeconds() {
    return this.lastAttemptedStoreEpochSeconds;
  }

  public RuleKeyStoreLogEntry setLastAttemptedStoreEpochSeconds(long lastAttemptedStoreEpochSeconds) {
    this.lastAttemptedStoreEpochSeconds = lastAttemptedStoreEpochSeconds;
    setLastAttemptedStoreEpochSecondsIsSet(true);
    return this;
  }

  public void unsetLastAttemptedStoreEpochSeconds() {
    __isset_bitfield = EncodingUtils.clearBit(__isset_bitfield, __LASTATTEMPTEDSTOREEPOCHSECONDS_ISSET_ID);
  }

  /** Returns true if field lastAttemptedStoreEpochSeconds is set (has been assigned a value) and false otherwise */
  public boolean isSetLastAttemptedStoreEpochSeconds() {
    return EncodingUtils.testBit(__isset_bitfield, __LASTATTEMPTEDSTOREEPOCHSECONDS_ISSET_ID);
  }

  public void setLastAttemptedStoreEpochSecondsIsSet(boolean value) {
    __isset_bitfield = EncodingUtils.setBit(__isset_bitfield, __LASTATTEMPTEDSTOREEPOCHSECONDS_ISSET_ID, value);
  }

  public long getLastFetchEpochSeconds() {
    return this.lastFetchEpochSeconds;
  }

  public RuleKeyStoreLogEntry setLastFetchEpochSeconds(long lastFetchEpochSeconds) {
    this.lastFetchEpochSeconds = lastFetchEpochSeconds;
    setLastFetchEpochSecondsIsSet(true);
    return this;
  }

  public void unsetLastFetchEpochSeconds() {
    __isset_bitfield = EncodingUtils.clearBit(__isset_bitfield, __LASTFETCHEPOCHSECONDS_ISSET_ID);
  }

  /** Returns true if field lastFetchEpochSeconds is set (has been assigned a value) and false otherwise */
  public boolean isSetLastFetchEpochSeconds() {
    return EncodingUtils.testBit(__isset_bitfield, __LASTFETCHEPOCHSECONDS_ISSET_ID);
  }

  public void setLastFetchEpochSecondsIsSet(boolean value) {
    __isset_bitfield = EncodingUtils.setBit(__isset_bitfield, __LASTFETCHEPOCHSECONDS_ISSET_ID, value);
  }

  public void setFieldValue(_Fields field, Object value) {
    switch (field) {
    case STORE_ID:
      if (value == null) {
        unsetStoreId();
      } else {
        setStoreId((String)value);
      }
      break;

    case STORE_TTLSECONDS:
      if (value == null) {
        unsetStoreTTLSeconds();
      } else {
        setStoreTTLSeconds((Long)value);
      }
      break;

    case LAST_STORE_EPOCH_SECONDS:
      if (value == null) {
        unsetLastStoreEpochSeconds();
      } else {
        setLastStoreEpochSeconds((Long)value);
      }
      break;

    case LAST_ATTEMPTED_STORE_EPOCH_SECONDS:
      if (value == null) {
        unsetLastAttemptedStoreEpochSeconds();
      } else {
        setLastAttemptedStoreEpochSeconds((Long)value);
      }
      break;

    case LAST_FETCH_EPOCH_SECONDS:
      if (value == null) {
        unsetLastFetchEpochSeconds();
      } else {
        setLastFetchEpochSeconds((Long)value);
      }
      break;

    }
  }

  public Object getFieldValue(_Fields field) {
    switch (field) {
    case STORE_ID:
      return getStoreId();

    case STORE_TTLSECONDS:
      return getStoreTTLSeconds();

    case LAST_STORE_EPOCH_SECONDS:
      return getLastStoreEpochSeconds();

    case LAST_ATTEMPTED_STORE_EPOCH_SECONDS:
      return getLastAttemptedStoreEpochSeconds();

    case LAST_FETCH_EPOCH_SECONDS:
      return getLastFetchEpochSeconds();

    }
    throw new IllegalStateException();
  }

  /** Returns true if field corresponding to fieldID is set (has been assigned a value) and false otherwise */
  public boolean isSet(_Fields field) {
    if (field == null) {
      throw new IllegalArgumentException();
    }

    switch (field) {
    case STORE_ID:
      return isSetStoreId();
    case STORE_TTLSECONDS:
      return isSetStoreTTLSeconds();
    case LAST_STORE_EPOCH_SECONDS:
      return isSetLastStoreEpochSeconds();
    case LAST_ATTEMPTED_STORE_EPOCH_SECONDS:
      return isSetLastAttemptedStoreEpochSeconds();
    case LAST_FETCH_EPOCH_SECONDS:
      return isSetLastFetchEpochSeconds();
    }
    throw new IllegalStateException();
  }

  @Override
  public boolean equals(Object that) {
    if (that == null)
      return false;
    if (that instanceof RuleKeyStoreLogEntry)
      return this.equals((RuleKeyStoreLogEntry)that);
    return false;
  }

  public boolean equals(RuleKeyStoreLogEntry that) {
    if (that == null)
      return false;

    boolean this_present_storeId = true && this.isSetStoreId();
    boolean that_present_storeId = true && that.isSetStoreId();
    if (this_present_storeId || that_present_storeId) {
      if (!(this_present_storeId && that_present_storeId))
        return false;
      if (!this.storeId.equals(that.storeId))
        return false;
    }

    boolean this_present_storeTTLSeconds = true && this.isSetStoreTTLSeconds();
    boolean that_present_storeTTLSeconds = true && that.isSetStoreTTLSeconds();
    if (this_present_storeTTLSeconds || that_present_storeTTLSeconds) {
      if (!(this_present_storeTTLSeconds && that_present_storeTTLSeconds))
        return false;
      if (this.storeTTLSeconds != that.storeTTLSeconds)
        return false;
    }

    boolean this_present_lastStoreEpochSeconds = true && this.isSetLastStoreEpochSeconds();
    boolean that_present_lastStoreEpochSeconds = true && that.isSetLastStoreEpochSeconds();
    if (this_present_lastStoreEpochSeconds || that_present_lastStoreEpochSeconds) {
      if (!(this_present_lastStoreEpochSeconds && that_present_lastStoreEpochSeconds))
        return false;
      if (this.lastStoreEpochSeconds != that.lastStoreEpochSeconds)
        return false;
    }

    boolean this_present_lastAttemptedStoreEpochSeconds = true && this.isSetLastAttemptedStoreEpochSeconds();
    boolean that_present_lastAttemptedStoreEpochSeconds = true && that.isSetLastAttemptedStoreEpochSeconds();
    if (this_present_lastAttemptedStoreEpochSeconds || that_present_lastAttemptedStoreEpochSeconds) {
      if (!(this_present_lastAttemptedStoreEpochSeconds && that_present_lastAttemptedStoreEpochSeconds))
        return false;
      if (this.lastAttemptedStoreEpochSeconds != that.lastAttemptedStoreEpochSeconds)
        return false;
    }

    boolean this_present_lastFetchEpochSeconds = true && this.isSetLastFetchEpochSeconds();
    boolean that_present_lastFetchEpochSeconds = true && that.isSetLastFetchEpochSeconds();
    if (this_present_lastFetchEpochSeconds || that_present_lastFetchEpochSeconds) {
      if (!(this_present_lastFetchEpochSeconds && that_present_lastFetchEpochSeconds))
        return false;
      if (this.lastFetchEpochSeconds != that.lastFetchEpochSeconds)
        return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    List<Object> list = new ArrayList<Object>();

    boolean present_storeId = true && (isSetStoreId());
    list.add(present_storeId);
    if (present_storeId)
      list.add(storeId);

    boolean present_storeTTLSeconds = true && (isSetStoreTTLSeconds());
    list.add(present_storeTTLSeconds);
    if (present_storeTTLSeconds)
      list.add(storeTTLSeconds);

    boolean present_lastStoreEpochSeconds = true && (isSetLastStoreEpochSeconds());
    list.add(present_lastStoreEpochSeconds);
    if (present_lastStoreEpochSeconds)
      list.add(lastStoreEpochSeconds);

    boolean present_lastAttemptedStoreEpochSeconds = true && (isSetLastAttemptedStoreEpochSeconds());
    list.add(present_lastAttemptedStoreEpochSeconds);
    if (present_lastAttemptedStoreEpochSeconds)
      list.add(lastAttemptedStoreEpochSeconds);

    boolean present_lastFetchEpochSeconds = true && (isSetLastFetchEpochSeconds());
    list.add(present_lastFetchEpochSeconds);
    if (present_lastFetchEpochSeconds)
      list.add(lastFetchEpochSeconds);

    return list.hashCode();
  }

  @Override
  public int compareTo(RuleKeyStoreLogEntry other) {
    if (!getClass().equals(other.getClass())) {
      return getClass().getName().compareTo(other.getClass().getName());
    }

    int lastComparison = 0;

    lastComparison = Boolean.valueOf(isSetStoreId()).compareTo(other.isSetStoreId());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetStoreId()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.storeId, other.storeId);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = Boolean.valueOf(isSetStoreTTLSeconds()).compareTo(other.isSetStoreTTLSeconds());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetStoreTTLSeconds()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.storeTTLSeconds, other.storeTTLSeconds);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = Boolean.valueOf(isSetLastStoreEpochSeconds()).compareTo(other.isSetLastStoreEpochSeconds());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetLastStoreEpochSeconds()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.lastStoreEpochSeconds, other.lastStoreEpochSeconds);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = Boolean.valueOf(isSetLastAttemptedStoreEpochSeconds()).compareTo(other.isSetLastAttemptedStoreEpochSeconds());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetLastAttemptedStoreEpochSeconds()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.lastAttemptedStoreEpochSeconds, other.lastAttemptedStoreEpochSeconds);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = Boolean.valueOf(isSetLastFetchEpochSeconds()).compareTo(other.isSetLastFetchEpochSeconds());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetLastFetchEpochSeconds()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.lastFetchEpochSeconds, other.lastFetchEpochSeconds);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    return 0;
  }

  public _Fields fieldForId(int fieldId) {
    return _Fields.findByThriftId(fieldId);
  }

  public void read(org.apache.thrift.protocol.TProtocol iprot) throws org.apache.thrift.TException {
    schemes.get(iprot.getScheme()).getScheme().read(iprot, this);
  }

  public void write(org.apache.thrift.protocol.TProtocol oprot) throws org.apache.thrift.TException {
    schemes.get(oprot.getScheme()).getScheme().write(oprot, this);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("RuleKeyStoreLogEntry(");
    boolean first = true;

    if (isSetStoreId()) {
      sb.append("storeId:");
      if (this.storeId == null) {
        sb.append("null");
      } else {
        sb.append(this.storeId);
      }
      first = false;
    }
    if (isSetStoreTTLSeconds()) {
      if (!first) sb.append(", ");
      sb.append("storeTTLSeconds:");
      sb.append(this.storeTTLSeconds);
      first = false;
    }
    if (isSetLastStoreEpochSeconds()) {
      if (!first) sb.append(", ");
      sb.append("lastStoreEpochSeconds:");
      sb.append(this.lastStoreEpochSeconds);
      first = false;
    }
    if (isSetLastAttemptedStoreEpochSeconds()) {
      if (!first) sb.append(", ");
      sb.append("lastAttemptedStoreEpochSeconds:");
      sb.append(this.lastAttemptedStoreEpochSeconds);
      first = false;
    }
    if (isSetLastFetchEpochSeconds()) {
      if (!first) sb.append(", ");
      sb.append("lastFetchEpochSeconds:");
      sb.append(this.lastFetchEpochSeconds);
      first = false;
    }
    sb.append(")");
    return sb.toString();
  }

  public void validate() throws org.apache.thrift.TException {
    // check for required fields
    // check for sub-struct validity
  }

  private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
    try {
      write(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(out)));
    } catch (org.apache.thrift.TException te) {
      throw new java.io.IOException(te);
    }
  }

  private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
    try {
      // it doesn't seem like you should have to do this, but java serialization is wacky, and doesn't call the default constructor.
      __isset_bitfield = 0;
      read(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(in)));
    } catch (org.apache.thrift.TException te) {
      throw new java.io.IOException(te);
    }
  }

  private static class RuleKeyStoreLogEntryStandardSchemeFactory implements SchemeFactory {
    public RuleKeyStoreLogEntryStandardScheme getScheme() {
      return new RuleKeyStoreLogEntryStandardScheme();
    }
  }

  private static class RuleKeyStoreLogEntryStandardScheme extends StandardScheme<RuleKeyStoreLogEntry> {

    public void read(org.apache.thrift.protocol.TProtocol iprot, RuleKeyStoreLogEntry struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TField schemeField;
      iprot.readStructBegin();
      while (true)
      {
        schemeField = iprot.readFieldBegin();
        if (schemeField.type == org.apache.thrift.protocol.TType.STOP) { 
          break;
        }
        switch (schemeField.id) {
          case 1: // STORE_ID
            if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
              struct.storeId = iprot.readString();
              struct.setStoreIdIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 2: // STORE_TTLSECONDS
            if (schemeField.type == org.apache.thrift.protocol.TType.I64) {
              struct.storeTTLSeconds = iprot.readI64();
              struct.setStoreTTLSecondsIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 3: // LAST_STORE_EPOCH_SECONDS
            if (schemeField.type == org.apache.thrift.protocol.TType.I64) {
              struct.lastStoreEpochSeconds = iprot.readI64();
              struct.setLastStoreEpochSecondsIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 4: // LAST_ATTEMPTED_STORE_EPOCH_SECONDS
            if (schemeField.type == org.apache.thrift.protocol.TType.I64) {
              struct.lastAttemptedStoreEpochSeconds = iprot.readI64();
              struct.setLastAttemptedStoreEpochSecondsIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 5: // LAST_FETCH_EPOCH_SECONDS
            if (schemeField.type == org.apache.thrift.protocol.TType.I64) {
              struct.lastFetchEpochSeconds = iprot.readI64();
              struct.setLastFetchEpochSecondsIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          default:
            org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
        }
        iprot.readFieldEnd();
      }
      iprot.readStructEnd();

      // check for required fields of primitive type, which can't be checked in the validate method
      struct.validate();
    }

    public void write(org.apache.thrift.protocol.TProtocol oprot, RuleKeyStoreLogEntry struct) throws org.apache.thrift.TException {
      struct.validate();

      oprot.writeStructBegin(STRUCT_DESC);
      if (struct.storeId != null) {
        if (struct.isSetStoreId()) {
          oprot.writeFieldBegin(STORE_ID_FIELD_DESC);
          oprot.writeString(struct.storeId);
          oprot.writeFieldEnd();
        }
      }
      if (struct.isSetStoreTTLSeconds()) {
        oprot.writeFieldBegin(STORE_TTLSECONDS_FIELD_DESC);
        oprot.writeI64(struct.storeTTLSeconds);
        oprot.writeFieldEnd();
      }
      if (struct.isSetLastStoreEpochSeconds()) {
        oprot.writeFieldBegin(LAST_STORE_EPOCH_SECONDS_FIELD_DESC);
        oprot.writeI64(struct.lastStoreEpochSeconds);
        oprot.writeFieldEnd();
      }
      if (struct.isSetLastAttemptedStoreEpochSeconds()) {
        oprot.writeFieldBegin(LAST_ATTEMPTED_STORE_EPOCH_SECONDS_FIELD_DESC);
        oprot.writeI64(struct.lastAttemptedStoreEpochSeconds);
        oprot.writeFieldEnd();
      }
      if (struct.isSetLastFetchEpochSeconds()) {
        oprot.writeFieldBegin(LAST_FETCH_EPOCH_SECONDS_FIELD_DESC);
        oprot.writeI64(struct.lastFetchEpochSeconds);
        oprot.writeFieldEnd();
      }
      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }

  }

  private static class RuleKeyStoreLogEntryTupleSchemeFactory implements SchemeFactory {
    public RuleKeyStoreLogEntryTupleScheme getScheme() {
      return new RuleKeyStoreLogEntryTupleScheme();
    }
  }

  private static class RuleKeyStoreLogEntryTupleScheme extends TupleScheme<RuleKeyStoreLogEntry> {

    @Override
    public void write(org.apache.thrift.protocol.TProtocol prot, RuleKeyStoreLogEntry struct) throws org.apache.thrift.TException {
      TTupleProtocol oprot = (TTupleProtocol) prot;
      BitSet optionals = new BitSet();
      if (struct.isSetStoreId()) {
        optionals.set(0);
      }
      if (struct.isSetStoreTTLSeconds()) {
        optionals.set(1);
      }
      if (struct.isSetLastStoreEpochSeconds()) {
        optionals.set(2);
      }
      if (struct.isSetLastAttemptedStoreEpochSeconds()) {
        optionals.set(3);
      }
      if (struct.isSetLastFetchEpochSeconds()) {
        optionals.set(4);
      }
      oprot.writeBitSet(optionals, 5);
      if (struct.isSetStoreId()) {
        oprot.writeString(struct.storeId);
      }
      if (struct.isSetStoreTTLSeconds()) {
        oprot.writeI64(struct.storeTTLSeconds);
      }
      if (struct.isSetLastStoreEpochSeconds()) {
        oprot.writeI64(struct.lastStoreEpochSeconds);
      }
      if (struct.isSetLastAttemptedStoreEpochSeconds()) {
        oprot.writeI64(struct.lastAttemptedStoreEpochSeconds);
      }
      if (struct.isSetLastFetchEpochSeconds()) {
        oprot.writeI64(struct.lastFetchEpochSeconds);
      }
    }

    @Override
    public void read(org.apache.thrift.protocol.TProtocol prot, RuleKeyStoreLogEntry struct) throws org.apache.thrift.TException {
      TTupleProtocol iprot = (TTupleProtocol) prot;
      BitSet incoming = iprot.readBitSet(5);
      if (incoming.get(0)) {
        struct.storeId = iprot.readString();
        struct.setStoreIdIsSet(true);
      }
      if (incoming.get(1)) {
        struct.storeTTLSeconds = iprot.readI64();
        struct.setStoreTTLSecondsIsSet(true);
      }
      if (incoming.get(2)) {
        struct.lastStoreEpochSeconds = iprot.readI64();
        struct.setLastStoreEpochSecondsIsSet(true);
      }
      if (incoming.get(3)) {
        struct.lastAttemptedStoreEpochSeconds = iprot.readI64();
        struct.setLastAttemptedStoreEpochSecondsIsSet(true);
      }
      if (incoming.get(4)) {
        struct.lastFetchEpochSeconds = iprot.readI64();
        struct.setLastFetchEpochSecondsIsSet(true);
      }
    }
  }

}

