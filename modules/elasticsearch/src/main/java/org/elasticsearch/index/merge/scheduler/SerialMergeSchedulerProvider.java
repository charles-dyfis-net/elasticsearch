/*
 * Licensed to Elastic Search and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Elastic Search licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.index.merge.scheduler;

import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.MergeScheduler;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.store.AlreadyClosedException;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.merge.policy.EnableMergePolicy;
import org.elasticsearch.index.settings.IndexSettings;
import org.elasticsearch.index.shard.AbstractIndexShardComponent;
import org.elasticsearch.index.shard.ShardId;

import java.io.IOException;

/**
 * @author kimchy (shay.banon)
 */
public class SerialMergeSchedulerProvider extends AbstractIndexShardComponent implements MergeSchedulerProvider {

    @Inject public SerialMergeSchedulerProvider(ShardId shardId, @IndexSettings Settings indexSettings) {
        super(shardId, indexSettings);
        logger.trace("using [serial] merge scheduler");
    }

    @Override public MergeScheduler newMergeScheduler() {
        return new CustomSerialMergeScheduler();
    }

    public static class CustomSerialMergeScheduler extends SerialMergeScheduler {

        @Override public void merge(IndexWriter writer) throws CorruptIndexException, IOException {
            try {
                // if merge is not enabled, don't do any merging...
                if (writer.getMergePolicy() instanceof EnableMergePolicy) {
                    if (!((EnableMergePolicy) writer.getMergePolicy()).isMergeEnabled()) {
                        return;
                    }
                }
            } catch (AlreadyClosedException e) {
                // called writer#getMergePolicy can cause an AlreadyClosed failure, so ignore it
                // since we are doing it on close, return here and don't do the actual merge
                // since we do it outside of a lock in the RobinEngine
                return;
            }
            super.merge(writer);
        }
    }
}
