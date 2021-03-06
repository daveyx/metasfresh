package de.metas.materialtracking.test.expectations;

import java.util.ArrayList;
import java.util.List;

import org.adempiere.util.lang.ObjectUtils;

/*
 * #%L
 * de.metas.materialtracking
 * %%
 * Copyright (C) 2015 metas GmbH
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

public class QualityInspectionExpectations
{
	private QualityInspectionLineExpectations qiLineExpectations;
	private List<QualityInspectionLineExpectations> allLineExpectations = new ArrayList<QualityInspectionLineExpectations>();

	public QualityInspectionLineExpectations getQiLineExpectations()
	{
		return qiLineExpectations;
	}

	public QualityInspectionExpectations setQiLineExpectations(QualityInspectionLineExpectations qiLineExpectations)
	{
		this.qiLineExpectations = qiLineExpectations;
		allLineExpectations.add(qiLineExpectations);
		return this;
	}

	public List<QualityInspectionLineExpectations> getAllLineExpectations()
	{
		return allLineExpectations;
	}

	@Override
	public String toString()
	{
		return ObjectUtils.toString(this);
	}

}
