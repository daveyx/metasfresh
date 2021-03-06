package de.metas.report.xls.engine;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.adempiere.ad.trx.api.ITrx;
import org.adempiere.exceptions.DBException;
import org.adempiere.util.Check;
import org.compiere.util.DB;

import com.google.common.base.MoreObjects;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

/*
 * #%L
 * de.metas.report.jasper.server.base
 * %%
 * Copyright (C) 2016 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

public class JdbcXlsDataSource implements IXlsDataSource
{
	public static final JdbcXlsDataSource of(final String sql)
	{
		return new JdbcXlsDataSource(sql);
	}

	private final String sql;

	private final Supplier<Collection<Object>> rowsSupplier = Suppliers.memoize(new Supplier<Collection<Object>>()
	{

		@Override
		public Collection<Object> get()
		{
			return retrieveRows();
		}
	});

	private JdbcXlsDataSource(final String sql)
	{
		super();
		Check.assumeNotNull(sql, "sql not null");
		this.sql = sql;
	}

	@Override
	public String toString()
	{
		return MoreObjects.toStringHelper(this)
				.add("sql", sql)
				.toString();
	}

	@Override
	public Collection<Object> getRows()
	{
		return rowsSupplier.get();
	}

	protected Collection<Object> retrieveRows()
	{
		final List<Object> result = new LinkedList<>();

		PreparedStatement pstmt = null;
		ResultSet rs = null;

		try
		{
			pstmt = DB.prepareStatement(sql, ITrx.TRXNAME_None);
			rs = pstmt.executeQuery();
			while (rs.next())
			{
				final Map<String, Object> row = retrieveRow(rs);
				result.add(row);
			}
		}
		catch (final SQLException e)
		{
			final Object[] sqlParams = new Object[] {};
			throw new DBException(e, sql, sqlParams);
		}
		finally
		{
			DB.close(rs, pstmt);
		}
		
		return result;
	}

	private Map<String, Object> retrieveRow(final ResultSet rs) throws SQLException
	{
		final Map<String, Object> row = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

		final ResultSetMetaData rsMetaData = rs.getMetaData();
		final int columnCount = rsMetaData.getColumnCount();
		for (int columnIndex = 1; columnIndex <= columnCount; columnIndex++)
		{
			final String columnName = rsMetaData.getColumnName(columnIndex);

			Object cellValue = rs.getObject(columnIndex);
			if (rs.wasNull())
			{
				cellValue = null;
			}

			row.put(columnName, cellValue);
		}

		return row;
	}
}
