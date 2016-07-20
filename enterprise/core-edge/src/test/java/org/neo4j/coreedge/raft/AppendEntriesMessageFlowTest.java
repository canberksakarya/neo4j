/*
 * Copyright (c) 2002-2016 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.coreedge.raft;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.neo4j.coreedge.raft.log.RaftLogEntry;
import org.neo4j.coreedge.raft.net.Outbound;
import org.neo4j.coreedge.server.CoreMember;
import org.neo4j.coreedge.server.RaftTestMemberSetBuilder;

import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.neo4j.coreedge.raft.TestMessageBuilders.appendEntriesRequest;
import static org.neo4j.coreedge.raft.TestMessageBuilders.appendEntriesResponse;
import static org.neo4j.coreedge.server.RaftTestMember.member;

@RunWith(MockitoJUnitRunner.class)
public class AppendEntriesMessageFlowTest
{
    private CoreMember myself = member( 0 );
    private CoreMember otherMember = member( 1 );

    private ReplicatedInteger data = ReplicatedInteger.valueOf( 1 );

    @Mock
    private Outbound<CoreMember, RaftMessages.RaftMessage> outbound;

    ReplicatedInteger data( int value )
    {
        return ReplicatedInteger.valueOf( value );
    }

    private RaftInstance raft;

    @Before
    public void setup()
    {
        // given
        raft = new RaftInstanceBuilder( myself, 3, RaftTestMemberSetBuilder.INSTANCE )
                .outbound( outbound )
                .build();
    }

    @Test
    public void shouldReturnFalseOnAppendRequestFromOlderTerm() throws Exception
    {
        // when
        raft.handle( appendEntriesRequest().from( otherMember ).leaderTerm( -1 ).prevLogIndex( 0 )
                .prevLogTerm( 0 ).leaderCommit( 0 ).build() );

        // then
        verify( outbound ).send( same( otherMember ),
                eq( appendEntriesResponse().from( myself ).term( 0 ).appendIndex( -1 ).matchIndex( -1 ).failure()
                        .build() ) );
    }

    @Test
    public void shouldReturnTrueOnAppendRequestWithFirstLogEntry() throws Exception
    {
        // when
        raft.handle( appendEntriesRequest().from( otherMember ).leaderTerm( 0 ).prevLogIndex( -1 )
                .prevLogTerm( -1 ).logEntry( new RaftLogEntry( 0, data ) ).leaderCommit( -1 ).build() );

        // then
        verify( outbound ).send( same( otherMember ), eq( appendEntriesResponse().
                appendIndex( 0 ).matchIndex( 0 ).from( myself ).term( 0 ).success().build() ) );
    }

    @Test
    public void shouldReturnTrueOnAppendRequestWithFirstLogEntryAndIgnorePrevTerm() throws Exception
    {
        // when
        raft.handle( appendEntriesRequest().from( otherMember ).leaderTerm( 0 ).prevLogIndex( -1 )
                .prevLogTerm( -1 ).logEntry( new RaftLogEntry( 0, data ) ).build() );

        // then
        verify( outbound ).send( same( otherMember ),
                eq( appendEntriesResponse().from( myself ).term( 0 ).appendIndex( 0 ).matchIndex( 0 ).success()
                        .build() ) );
    }

    @Test
    public void shouldReturnFalseOnAppendRequestWhenPrevLogEntryNotMatched() throws Exception
    {
        // when
        raft.handle( appendEntriesRequest().from( otherMember ).leaderTerm( 0 ).prevLogIndex( 0 )
                .prevLogTerm( 0 ).logEntry( new RaftLogEntry( 0, data ) ).build() );

        // then
        verify( outbound ).send( same( otherMember ),
                eq( appendEntriesResponse().from( myself ).term( 0 ).failure().build() ) );
    }

    @Test
    public void shouldAcceptSequenceOfAppendEntries() throws Exception
    {
        // when
        raft.handle( appendEntriesRequest().from( otherMember ).leaderTerm( 0 ).prevLogIndex( -1 )
                .prevLogTerm( -1 ).logEntry( new RaftLogEntry( 0, data( 1 ) ) ).leaderCommit( -1 ).build() );
        raft.handle( appendEntriesRequest().from( otherMember ).leaderTerm( 0 ).prevLogIndex( 0 )
                .prevLogTerm( 0 ).logEntry( new RaftLogEntry( 0, data( 2 ) ) ).leaderCommit( -1 ).build() );
        raft.handle( appendEntriesRequest().from( otherMember ).leaderTerm( 0 ).prevLogIndex( 1 )
                .prevLogTerm( 0 ).logEntry( new RaftLogEntry( 0, data( 3 ) ) ).leaderCommit( 0 ).build() );

        raft.handle( appendEntriesRequest().from( otherMember ).leaderTerm( 1 ).prevLogIndex( 2 )
                .prevLogTerm( 0 ).logEntry( new RaftLogEntry( 1, data( 4 ) ) ).leaderCommit( 1 ).build() );
        raft.handle( appendEntriesRequest().from( otherMember ).leaderTerm( 1 ).prevLogIndex( 3 )
                .prevLogTerm( 1 ).logEntry( new RaftLogEntry( 1, data( 5 ) ) ).leaderCommit( 2 ).build() );
        raft.handle( appendEntriesRequest().from( otherMember ).leaderTerm( 1 ).prevLogIndex( 4 )
                .prevLogTerm( 1 ).logEntry( new RaftLogEntry( 1, data( 6 ) ) ).leaderCommit( 4 ).build() );

        // then
        InOrder invocationOrder = inOrder( outbound );
        invocationOrder.verify( outbound, times( 1 ) ).send( same( otherMember ), eq( appendEntriesResponse().from(
                myself ).term( 0 ).appendIndex( 0 ).matchIndex( 0 ).success().build() ) );
        invocationOrder.verify( outbound, times( 1 ) ).send( same( otherMember ), eq( appendEntriesResponse().from(
                myself ).term( 0 ).appendIndex( 1 ).matchIndex( 1 ).success().build() ) );
        invocationOrder.verify( outbound, times( 1 ) ).send( same( otherMember ), eq( appendEntriesResponse().from(
                myself ).term( 0 ).appendIndex( 2 ).matchIndex( 2 ).success().build() ) );

        invocationOrder.verify( outbound, times( 1 ) ).send( same( otherMember ), eq( appendEntriesResponse().from(
                myself ).term( 1 ).appendIndex( 3 ).matchIndex( 3 ).success().build() ) );
        invocationOrder.verify( outbound, times( 1 ) ).send( same( otherMember ), eq( appendEntriesResponse().from(
                myself ).term( 1 ).appendIndex( 4 ).matchIndex( 4 ).success().build() ) );
        invocationOrder.verify( outbound, times( 1 ) ).send( same( otherMember ), eq( appendEntriesResponse().from(
                myself ).term( 1 ).appendIndex( 5 ).matchIndex( 5 ).success().build() ) );
    }

    @Test
    public void shouldReturnFalseIfLogHistoryDoesNotMatch() throws Exception
    {
        raft.handle( appendEntriesRequest().from( otherMember ).leaderTerm( 0 ).prevLogIndex( -1 )
                .prevLogTerm( -1 ).logEntry( new RaftLogEntry( 0, data( 1 ) ) ).build() );
        raft.handle( appendEntriesRequest().from( otherMember ).leaderTerm( 0 ).prevLogIndex( 0 )
                .prevLogTerm( 0 ).logEntry( new RaftLogEntry( 0, data( 2 ) ) ).build() );
        raft.handle( appendEntriesRequest().from( otherMember ).leaderTerm( 0 ).prevLogIndex( 1 )
                .prevLogTerm( 0 ).logEntry( new RaftLogEntry( 0, data( 3 ) ) ).build() ); // will conflict

        // when
        raft.handle( appendEntriesRequest().from( otherMember ).leaderTerm( 2 ).prevLogIndex( 2 )
                .prevLogTerm( 1 ).logEntry( new RaftLogEntry( 2, data( 4 ) ) ).build() ); // should reply false
        // because of prevLogTerm

        // then
        InOrder invocationOrder = inOrder( outbound );
        invocationOrder.verify( outbound, times( 1 ) ).send( same( otherMember ), eq( appendEntriesResponse().from(
                myself ).term( 0 ).matchIndex( 0 ).appendIndex( 0 ).success().build() ) );
        invocationOrder.verify( outbound, times( 1 ) ).send( same( otherMember ), eq( appendEntriesResponse().from(
                myself ).term( 0 ).matchIndex( 1 ).appendIndex( 1 ).success().build() ) );
        invocationOrder.verify( outbound, times( 1 ) ).send( same( otherMember ), eq( appendEntriesResponse().from(
                myself ).term( 0 ).matchIndex( 2 ).appendIndex( 2 ).success().build() ) );
        invocationOrder.verify( outbound, times( 1 ) ).send( same( otherMember ), eq( appendEntriesResponse().from(
                myself ).term( 2 ).matchIndex( -1 ).appendIndex( 2 ).failure().build() ) );
    }
}