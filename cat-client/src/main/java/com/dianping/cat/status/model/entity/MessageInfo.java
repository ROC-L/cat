/* THIS FILE WAS AUTO GENERATED BY codegen-maven-plugin, DO NOT EDIT IT */
package com.dianping.cat.status.model.entity;

import com.dianping.cat.status.model.BaseEntity;
import com.dianping.cat.status.model.IVisitor;

public class MessageInfo extends BaseEntity<MessageInfo> {
   private long m_produced;

   private long m_overflowed;

   private long m_bytes;

   public MessageInfo() {
   }

   @Override
   public void accept(IVisitor visitor) {
      visitor.visitMessage(this);
   }

   @Override
   public boolean equals(Object obj) {
      if (obj instanceof MessageInfo) {
         MessageInfo _o = (MessageInfo) obj;

         if (getProduced() != _o.getProduced()) {
            return false;
         }

         if (getOverflowed() != _o.getOverflowed()) {
            return false;
         }

         if (getBytes() != _o.getBytes()) {
            return false;
         }


         return true;
      }

      return false;
   }

   public long getBytes() {
      return m_bytes;
   }

   public long getOverflowed() {
      return m_overflowed;
   }

   public long getProduced() {
      return m_produced;
   }

   @Override
   public int hashCode() {
      int hash = 0;

      hash = hash * 31 + (int) (m_produced ^ (m_produced >>> 32));
      hash = hash * 31 + (int) (m_overflowed ^ (m_overflowed >>> 32));
      hash = hash * 31 + (int) (m_bytes ^ (m_bytes >>> 32));

      return hash;
   }

   @Override
   public void mergeAttributes(MessageInfo other) {
      m_produced = other.getProduced();

      m_overflowed = other.getOverflowed();

      m_bytes = other.getBytes();
   }

   public MessageInfo setBytes(long bytes) {
      m_bytes = bytes;
      return this;
   }

   public MessageInfo setOverflowed(long overflowed) {
      m_overflowed = overflowed;
      return this;
   }

   public MessageInfo setProduced(long produced) {
      m_produced = produced;
      return this;
   }

}
