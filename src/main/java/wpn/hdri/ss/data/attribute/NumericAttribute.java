/*
 * The main contributor to this project is Institute of Materials Research,
 * Helmholtz-Zentrum Geesthacht,
 * Germany.
 *
 * This project is a contribution of the Helmholtz Association Centres and
 * Technische Universitaet Muenchen to the ESS Design Update Phase.
 *
 * The project's funding reference is FKZ05E11CG1.
 *
 * Copyright (c) 2012. Institute of Materials Research,
 * Helmholtz-Zentrum Geesthacht,
 * Germany.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA
 */

package wpn.hdri.ss.data.attribute;

import wpn.hdri.ss.data.Interpolation;
import wpn.hdri.ss.data.Timestamp;

import javax.annotation.concurrent.NotThreadSafe;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.ParsePosition;
import java.util.Map;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * @author Igor Khokhriakov <igor.khokhriakov@hzg.de>
 * @since 02.05.12
 */
@NotThreadSafe
public final class NumericAttribute<T extends Number> extends Attribute<T> {

    public static final double DEFAULT_PRECISION = 0.;

    private static final DecimalFormat DECIMAL_FORMAT = (DecimalFormat) DecimalFormat.getInstance();

    static {
        DECIMAL_FORMAT.setParseBigDecimal(true);
    }

    private static final ParsePosition POSITION = new ParsePosition(0);
    private final BigDecimal precision;

    private final ConcurrentNavigableMap<Timestamp, BigDecimal> numericValues = new ConcurrentSkipListMap<Timestamp, BigDecimal>();

    public NumericAttribute(String deviceName, String name, String alias, Interpolation interpolation, BigDecimal precision) {
        super(deviceName, name, alias, interpolation);
        this.precision = precision;
    }

    /**
     * For tests
     *
     * @param deviceName
     * @param name
     * @param interpolation
     * @param precision
     */
    public NumericAttribute(String deviceName, String name, Interpolation interpolation, double precision) {
        this(deviceName, name, name, interpolation, BigDecimal.valueOf(precision));
    }

    /**
     * For tests
     *
     * @param deviceName
     * @param name
     * @param interpolation
     */
    public NumericAttribute(String deviceName, String name, Interpolation interpolation) {
        this(deviceName, name, interpolation, DEFAULT_PRECISION);
    }

    /**
     * Adds a value if it satisfies the precision test. If by ane means value can not be converted to BigDecimal it is skipped.
     * <p/>
     * Normally does not permit null values, only if it is the first value is being added
     *
     * @param value the value
     */
    @Override
    @SuppressWarnings("unchecked")
    protected boolean addValueInternal(AttributeValue<T> value) {
        if (value.isNull()) {
            return true;
        }

        //in general prefer new BigDecimal(String) over new BigDecimal(double). See Effective Java Item 31
        String text = String.valueOf(value.getValue().get());
        BigDecimal decimal = (BigDecimal) DECIMAL_FORMAT.parse(text, POSITION);
        if (decimal == null) {
            try {
                decimal = new BigDecimal(text);
            } catch (NumberFormatException e) {
                LOGGER.error(e);
                return false;
            }
        }

        Map.Entry<Timestamp, BigDecimal> lastNumericEntry = numericValues.floorEntry(value.getReadTimestamp());
        if (lastNumericEntry == null) {
            numericValues.putIfAbsent(value.getReadTimestamp(), decimal);
            return true;
        }

        BigDecimal lastDecimal = lastNumericEntry.getValue();

        // |x - y| > precision
        if (decimal.subtract(lastDecimal).abs().compareTo(precision) > 0) {
            numericValues.putIfAbsent(value.getReadTimestamp(), decimal);
            return true;
        }
        return false;
    }

    @Override
    public void clear() {
        super.clear();
        numericValues.clear();
    }
}