package ca.uhn.fhir.model.primitive;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.time.FastDateFormat;

import ca.uhn.fhir.model.api.BasePrimitive;
import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import ca.uhn.fhir.parser.DataFormatException;

public abstract class BaseDateTimeDt extends BasePrimitive<Date> {

	private static final FastDateFormat ourHumanDateFormat = FastDateFormat.getDateInstance(FastDateFormat.MEDIUM);
	private static final FastDateFormat ourHumanDateTimeFormat = FastDateFormat.getDateTimeInstance(FastDateFormat.MEDIUM, FastDateFormat.MEDIUM);

	private int myFractionalSeconds;
	private TemporalPrecisionEnum myPrecision = TemporalPrecisionEnum.SECOND;
	private TimeZone myTimeZone;

	private boolean myTimeZoneZulu = false;

	/**
	 * Constructor
	 */
	public BaseDateTimeDt() {
		// nothing
	}

	/**
	 * Constructor
	 * 
	 * @throws DataFormatException
	 *            If the specified precision is not allowed for this type
	 */
	public BaseDateTimeDt(Date theDate, TemporalPrecisionEnum thePrecision) {
		setValue(theDate, thePrecision);
		if (isPrecisionAllowed(thePrecision) == false) {
			throw new DataFormatException("Invalid date/time string (datatype " + getClass().getSimpleName() + " does not support " + thePrecision + " precision): " + theDate);
		}
	}

	/**
	 * Constructor
	 */
	public BaseDateTimeDt(Date theDate, TemporalPrecisionEnum thePrecision, TimeZone theTimeZone) {
		this(theDate, thePrecision);
		setTimeZone(theTimeZone);
	}

	/**
	 * Constructor
	 * 
	 * @throws DataFormatException
	 *            If the specified precision is not allowed for this type
	 */
	public BaseDateTimeDt(String theString) {
		setValueAsString(theString);
		if (isPrecisionAllowed(getPrecision()) == false) {
			throw new DataFormatException("Invalid date/time string (datatype " + getClass().getSimpleName() + " does not support " + getPrecision() + " precision): " + theString);
		}
	}

	private void clearTimeZone() {
		myTimeZone = null;
		myTimeZoneZulu = false;
	}

	@Override
	protected String encode(Date theValue) {
		if (theValue == null) {
			return null;
		} else {
			GregorianCalendar cal;
			if (myTimeZoneZulu) {
				cal = new GregorianCalendar(TimeZone.getTimeZone("GMT"));
			} else if (myTimeZone != null) {
				cal = new GregorianCalendar(myTimeZone);
			} else {
				cal = new GregorianCalendar();
			}
			cal.setTime(theValue);

			StringBuilder b = new StringBuilder();
			leftPadWithZeros(cal.get(Calendar.YEAR), 4, b);
			if (myPrecision.ordinal() > TemporalPrecisionEnum.YEAR.ordinal()) {
				b.append('-');
				leftPadWithZeros(cal.get(Calendar.MONTH) + 1, 2, b);
				if (myPrecision.ordinal() > TemporalPrecisionEnum.MONTH.ordinal()) {
					b.append('-');
					leftPadWithZeros(cal.get(Calendar.DATE), 2, b);
					if (myPrecision.ordinal() > TemporalPrecisionEnum.DAY.ordinal()) {
						b.append('T');
						leftPadWithZeros(cal.get(Calendar.HOUR_OF_DAY), 2, b);
						b.append(':');
						leftPadWithZeros(cal.get(Calendar.MINUTE), 2, b);
						if (myPrecision.ordinal() > TemporalPrecisionEnum.MINUTE.ordinal()) {
							b.append(':');
							leftPadWithZeros(cal.get(Calendar.SECOND), 2, b);
							if (myPrecision.ordinal() > TemporalPrecisionEnum.SECOND.ordinal()) {
								b.append('.');
								leftPadWithZeros(myFractionalSeconds, 3, b);
							}
						}

						if (myTimeZoneZulu) {
							b.append('Z');
						} else if (myTimeZone != null) {
							int offset = myTimeZone.getOffset(theValue.getTime());
							if (offset >= 0) {
								b.append('+');
							} else {
								b.append('-');
								offset = Math.abs(offset);
							}

							int hoursOffset = (int) (offset / DateUtils.MILLIS_PER_HOUR);
							leftPadWithZeros(hoursOffset, 2, b);
							b.append(':');
							int minutesOffset = (int) (offset % DateUtils.MILLIS_PER_HOUR);
							minutesOffset = (int) (minutesOffset / DateUtils.MILLIS_PER_MINUTE);
							leftPadWithZeros(minutesOffset, 2, b);
						}
					}
				}
			}
			return b.toString();
		}
	}

	/**
	 * Returns the default precision for the given datatype
	 */
	protected abstract TemporalPrecisionEnum getDefaultPrecisionForDatatype();

	private int getOffsetIndex(String theValueString) {
		int plusIndex = theValueString.indexOf('+', 16);
		int minusIndex = theValueString.indexOf('-', 16);
		int zIndex = theValueString.indexOf('Z', 16);
		int retVal = Math.max(Math.max(plusIndex, minusIndex), zIndex);
		if (retVal == -1) {
			return -1;
		}
		if ((retVal - 2) != (plusIndex + minusIndex + zIndex)) {
			// This means we have more than one separator
			throw new DataFormatException("Invalid FHIR date/time string: " + theValueString);
		}
		return retVal;
	}

	/**
	 * Gets the precision for this datatype (using the default for the given type if not set)
	 * 
	 * @see #setPrecision(TemporalPrecisionEnum)
	 */
	public TemporalPrecisionEnum getPrecision() {
		if (myPrecision == null) {
			return getDefaultPrecisionForDatatype();
		}
		return myPrecision;
	}

	/**
	 * Returns the TimeZone associated with this dateTime's value. May return <code>null</code> if no timezone was
	 * supplied.
	 */
	public TimeZone getTimeZone() {
		if (myTimeZoneZulu) {
			return TimeZone.getTimeZone("Z");
		}
		return myTimeZone;
	}

	private boolean hasOffset(String theValue) {
		boolean inTime = false;
		for (int i = 0; i < theValue.length(); i++) {
			switch (theValue.charAt(i)) {
			case 'T':
				inTime = true;
				break;
			case '+':
			case '-':
				if (inTime) {
					return true;
				}
				break;
			}
		}
		return false;
	}

	/**
	 * To be implemented by subclasses to indicate whether the given precision is allowed by this type
	 */
	abstract boolean isPrecisionAllowed(TemporalPrecisionEnum thePrecision);

	public boolean isTimeZoneZulu() {
		return myTimeZoneZulu;
	}

	/**
	 * Returns <code>true</code> if this object represents a date that is today's date
	 * 
	 * @throws NullPointerException
	 *            if {@link #getValue()} returns <code>null</code>
	 */
	public boolean isToday() {
		Validate.notNull(getValue(), getClass().getSimpleName() + " contains null value");
		return DateUtils.isSameDay(new Date(), getValue());
	}

	private void leftPadWithZeros(int theInteger, int theLength, StringBuilder theTarget) {
		String string = Integer.toString(theInteger);
		for (int i = string.length(); i < theLength; i++) {
			theTarget.append('0');
		}
		theTarget.append(string);
	}

	@Override
	protected Date parse(String theValue) throws DataFormatException {
		Calendar cal = new GregorianCalendar(0, 0, 0);
		cal.setTimeZone(TimeZone.getDefault());
		int length = theValue.length();

		if (length == 0) {
			return null;
		}
		if (length < 4) {
			throwBadDateFormat(theValue);
		}

		TemporalPrecisionEnum precision = null;
		cal.set(Calendar.YEAR, parseInt(theValue, theValue.substring(0, 4), 0, 9999));
		precision = TemporalPrecisionEnum.YEAR;
		if (length > 4) {
			validateCharAtIndexIs(theValue, 4, '-');
			validateLengthIsAtLeast(theValue, 7);
			int monthVal = parseInt(theValue, theValue.substring(5, 7), 1, 12) - 1;
			cal.set(Calendar.MONTH, monthVal);
			precision = TemporalPrecisionEnum.MONTH;
			if (length > 7) {
				validateCharAtIndexIs(theValue, 7, '-');
				validateLengthIsAtLeast(theValue, 10);
				cal.set(Calendar.DATE, 1); // for some reason getActualMaximum works incorrectly if date isn't set
				int actualMaximum = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
				cal.set(Calendar.DAY_OF_MONTH, parseInt(theValue, theValue.substring(8, 10), 1, actualMaximum));
				precision = TemporalPrecisionEnum.DAY;
				if (length > 10) {
					validateLengthIsAtLeast(theValue, 17);
					validateCharAtIndexIs(theValue, 10, 'T'); // yyyy-mm-ddThh:mm:ss
					int offsetIdx = getOffsetIndex(theValue);
					String time;
					if (offsetIdx == -1) {
						//throwBadDateFormat(theValue);
						// No offset - should this be an error?
						time = theValue.substring(11);
					} else {
						time = theValue.substring(11, offsetIdx);
						String offsetString = theValue.substring(offsetIdx);
						setTimeZone(theValue, offsetString);
						cal.setTimeZone(getTimeZone());
					}
					int timeLength = time.length();

					validateCharAtIndexIs(theValue, 13, ':');
					cal.set(Calendar.HOUR_OF_DAY, parseInt(theValue, theValue.substring(11, 13), 0, 23));
					cal.set(Calendar.MINUTE, parseInt(theValue, theValue.substring(14, 16), 0, 59));
					precision = TemporalPrecisionEnum.MINUTE;
					if (timeLength > 5) {
						validateLengthIsAtLeast(theValue, 19);
						validateCharAtIndexIs(theValue, 16, ':'); // yyyy-mm-ddThh:mm:ss
						cal.set(Calendar.SECOND, parseInt(theValue, theValue.substring(17, 19), 0, 59));
						precision = TemporalPrecisionEnum.SECOND;
						if (timeLength > 8) {
							validateCharAtIndexIs(theValue, 19, '.'); // yyyy-mm-ddThh:mm:ss.SSSS
							validateLengthIsAtLeast(theValue, 20);
							int endIndex = getOffsetIndex(theValue);
							if (endIndex == -1) {
								endIndex = theValue.length();
							}
							int millis;
							if (endIndex > 23) {
								myFractionalSeconds = parseInt(theValue, theValue.substring(20, endIndex), 0, Integer.MAX_VALUE);
								endIndex = 23;
								String millisString = theValue.substring(20, endIndex);
								millis = parseInt(theValue, millisString, 0, 999);
							} else {
								String millisString = theValue.substring(20, endIndex);
								millis = parseInt(theValue, millisString, 0, 999);
								myFractionalSeconds = millis;
							}
							cal.set(Calendar.MILLISECOND, millis);
							precision = TemporalPrecisionEnum.MILLI;
						}
					}
				}
			} else {
				cal.set(Calendar.DATE, 1);
			}
		} else {
			cal.set(Calendar.DATE, 1);
		}

		setPrecision(precision);
		return cal.getTime();

	}

	private int parseInt(String theValue, String theSubstring, int theLowerBound, int theUpperBound) {
		int retVal = 0;
		try {
			retVal = Integer.parseInt(theSubstring);
		} catch (NumberFormatException e) {
			throwBadDateFormat(theValue);
		}

		if (retVal < theLowerBound || retVal > theUpperBound) {
			throwBadDateFormat(theValue);
		}

		return retVal;
	}

	/**
	 * Sets the precision for this datatype
	 * 
	 * @throws DataFormatException
	 */
	public void setPrecision(TemporalPrecisionEnum thePrecision) throws DataFormatException {
		if (thePrecision == null) {
			throw new NullPointerException("Precision may not be null");
		}
		myPrecision = thePrecision;
		updateStringValue();
	}

	private BaseDateTimeDt setTimeZone(String theWholeValue, String theValue) {

		if (isBlank(theValue)) {
			throwBadDateFormat(theWholeValue);
		} else if (theValue.charAt(0) == 'Z') {
			clearTimeZone();
			setTimeZoneZulu(true);
		} else if (theValue.length() != 5) {
			throwBadDateFormat(theWholeValue, "Timezone offset must be in the form \"Z\", \"-HH:mm\", or \"+HH:mm\"");
		} else if (theValue.charAt(3) != ':' || !(theValue.charAt(0) == '+' || theValue.charAt(0) == '-')) {
			throwBadDateFormat(theWholeValue, "Timezone offset must be in the form \"Z\", \"-HH:mm\", or \"+HH:mm\"");
		} else {
			parseInt(theWholeValue, theValue.substring(0, 2), 0, 23);
			parseInt(theWholeValue, theValue.substring(3, 5), 0, 59);
			clearTimeZone();
			setTimeZone(TimeZone.getTimeZone("GMT" + theValue));
		}

		return this;
	}

	public BaseDateTimeDt setTimeZone(TimeZone theTimeZone) {
		myTimeZone = theTimeZone;
		updateStringValue();
		return this;
	}

	public BaseDateTimeDt setTimeZoneZulu(boolean theTimeZoneZulu) {
		myTimeZoneZulu = theTimeZoneZulu;
		updateStringValue();
		return this;
	}

	/**
	 * Sets the value for this type using the given Java Date object as the time, and using the default precision for
	 * this datatype, as well as the local timezone as determined by the local operating
	 * system. Both of these properties may be modified in subsequent calls if neccesary.
	 */
	@Override
	public BaseDateTimeDt setValue(Date theValue) {
		setValue(theValue, getDefaultPrecisionForDatatype());
		return this;
	}

	/**
	 * Sets the value for this type using the given Java Date object as the time, and using the specified precision, as
	 * well as the local timezone as determined by the local operating system. Both of
	 * these properties may be modified in subsequent calls if neccesary.
	 * 
	 * @param theValue
	 *           The date value
	 * @param thePrecision
	 *           The precision
	 * @throws DataFormatException
	 */
	public void setValue(Date theValue, TemporalPrecisionEnum thePrecision) throws DataFormatException {
		setTimeZone(TimeZone.getDefault());
		myPrecision = thePrecision;
		super.setValue(theValue);
		myFractionalSeconds = 0;
		if (theValue != null) {
			myFractionalSeconds = (int) (theValue.getTime() % 1000);
		}
	}

	@Override
	public void setValueAsString(String theValue) throws DataFormatException {
		clearTimeZone();
		super.setValueAsString(theValue);
	}

	private void throwBadDateFormat(String theValue) {
		throw new DataFormatException("Invalid date/time format: \"" + theValue + "\"");
	}

	private void throwBadDateFormat(String theValue, String theMesssage) {
		throw new DataFormatException("Invalid date/time format: \"" + theValue + "\": " + theMesssage);
	}

	/**
	 * Returns a human readable version of this date/time using the system local format.
	 * <p>
	 * <b>Note on time zones:</b> This method renders the value using the time zone that is contained within the value.
	 * For example, if this date object contains the value "2012-01-05T12:00:00-08:00",
	 * the human display will be rendered as "12:00:00" even if the application is being executed on a system in a
	 * different time zone. If this behaviour is not what you want, use
	 * {@link #toHumanDisplayLocalTimezone()} instead.
	 * </p>
	 */
	public String toHumanDisplay() {
		TimeZone tz = getTimeZone();
		Calendar value = tz != null ? Calendar.getInstance(tz) : Calendar.getInstance();
		value.setTime(getValue());

		switch (getPrecision()) {
		case YEAR:
		case MONTH:
		case DAY:
			return ourHumanDateFormat.format(value);
		case MILLI:
		case SECOND:
		default:
			return ourHumanDateTimeFormat.format(value);
		}
	}

	/**
	 * Returns a human readable version of this date/time using the system local format, converted to the local timezone
	 * if neccesary.
	 * 
	 * @see #toHumanDisplay() for a method which does not convert the time to the local timezone before rendering it.
	 */
	public String toHumanDisplayLocalTimezone() {
		switch (getPrecision()) {
		case YEAR:
		case MONTH:
		case DAY:
			return ourHumanDateFormat.format(getValue());
		case MILLI:
		case SECOND:
		default:
			return ourHumanDateTimeFormat.format(getValue());
		}
	}

	private void validateCharAtIndexIs(String theValue, int theIndex, char theChar) {
		if (theValue.charAt(theIndex) != theChar) {
			throwBadDateFormat(theValue, "Expected character '" + theChar + "' at index " + theIndex + " but found " + theValue.charAt(theIndex));
		}
	}

	private void validateLengthIsAtLeast(String theValue, int theLength) {
		if (theValue.length() < theLength) {
			throwBadDateFormat(theValue);
		}
	}

}
