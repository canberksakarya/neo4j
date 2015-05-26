/*
 * Copyright (c) 2002-2015 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel.impl.transaction.log.entry;

import java.io.IOException;

import org.neo4j.kernel.impl.transaction.TransactionRepresentation;
import org.neo4j.kernel.impl.transaction.log.LogPosition;

public interface LogEntryWriter
{
    void writeStartEntry( int masterId, int authorId, long timeWritten, long latestCommittedTxWhenStarted,
            byte[] additionalHeaderData ) throws IOException;

    void serialize( TransactionRepresentation tx ) throws IOException;

    void writeCommitEntry( long transactionId, long timeWritten ) throws IOException;

    void writeCheckPointEntry( long transactionId, LogPosition logPosition ) throws IOException;
}
