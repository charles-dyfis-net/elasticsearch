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

package org.elasticsearch.test.integration.search.scan;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.collect.Sets;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.test.integration.AbstractNodesTests;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Set;

import static org.elasticsearch.index.query.xcontent.QueryBuilders.*;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

public class SearchScanTests extends AbstractNodesTests {

    private Client client;

    @BeforeClass public void createNodes() throws Exception {
        startNode("node1");
        startNode("node2");
        client = getClient();
    }

    @AfterClass public void closeNodes() {
        client.close();
        closeAllNodes();
    }

    protected Client getClient() {
        return client("node1");
    }

    @Test public void testSimpleScroll() throws Exception {
        try {
            client.admin().indices().prepareDelete("test").execute().actionGet();
        } catch (Exception e) {
            // ignore
        }
        client.admin().indices().prepareCreate("test").setSettings(ImmutableSettings.settingsBuilder().put("index.number_of_shards", 3)).execute().actionGet();
        client.admin().cluster().prepareHealth().setWaitForGreenStatus().execute().actionGet();

        Set<String> ids = Sets.newHashSet();
        Set<String> expectedIds = Sets.newHashSet();
        for (int i = 0; i < 100; i++) {
            String id = Integer.toString(i);
            expectedIds.add(id);
            client.prepareIndex("test", "type1", id).setSource("field", i).execute().actionGet();
        }

        client.admin().indices().prepareRefresh().execute().actionGet();

        SearchResponse searchResponse = client.prepareSearch()
                .setSearchType(SearchType.SCAN)
                .setQuery(matchAllQuery())
                .setSize(7)
                .setScroll(TimeValue.timeValueMinutes(2))
                .execute().actionGet();

        assertThat(searchResponse.hits().totalHits(), equalTo(100l));

        // start scrolling, until we get not results
        while (true) {
            searchResponse = client.prepareSearchScroll(searchResponse.scrollId()).setScroll(TimeValue.timeValueMinutes(2)).execute().actionGet();
            assertThat(searchResponse.failedShards(), equalTo(0));
            for (SearchHit hit : searchResponse.hits()) {
                assertThat(hit.id() + "should not exists in the result set", ids.contains(hit.id()), equalTo(false));
                ids.add(hit.id());
            }
            if (searchResponse.hits().totalHits() == 0) {
                break;
            }
        }

        assertThat(expectedIds, equalTo(ids));
    }
}