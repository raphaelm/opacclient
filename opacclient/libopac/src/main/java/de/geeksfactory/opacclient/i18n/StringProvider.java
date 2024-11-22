/**
 * Copyright (C) 2013 by Johan von Forstner under the MIT license:
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software
 * is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */
package de.geeksfactory.opacclient.i18n;

import de.geeksfactory.opacclient.objects.SearchResult;

/**
 * The StringProvider interface exposes an abstract method of translating strings from the OpacApi
 * classes. Since version 3.2.1, it is highly discouraged to hardcode any string into the OpacApi
 * implementations which might be displayed to the user, as this makes internationalization very
 * hard.
 *
 * While Android has a powerful i18n library using the 'string resources' concept, our apis.*
 * subpackage should not depend on Android, to make it seperable from the app, e.g. for running our
 * test suite on a PC. Therefore, this abstract interface provides access to Android's i18n API on
 * Android devices. It currently performs a no-op on other devices.
 *
 * @author Johan v. Forstner
 * @since 3.2.1
 */
public interface StringProvider {
    String LIMITED_NUM_OF_CRITERIA = "limited_num_of_criteria";
    String NO_CRITERIA_INPUT = "no_criteria_input";
    String COMBINATION_NOT_SUPPORTED = "combination_not_supported";
    String UNKNOWN_ERROR = "unknown_error";
    String UNKNOWN_ERROR_WITH_DESCRIPTION = "unknown_error_with_description";
    String UNKNOWN_ERROR_ACCOUNT = "unknown_error_account";
    String UNKNOWN_ERROR_ACCOUNT_WITH_DESCRIPTION =
            "unknown_error_account_with_description";
    String INTERNAL_ERROR = "internal_error";
    String INTERNAL_ERROR_WITH_DESCRIPTION = "internal_error_with_description";
    String LOGIN_FAILED = "login_failed";
    String COULD_NOT_LOAD_ACCOUNT = "could_not_load_account";
    String CONNECTION_ERROR = "api_connection_error";
    String LENT_UNTIL = "lent_until";
    String SUBTITLE = "subtitle";
    String PICA_WHICH_COPY = "pica_which_copy";
    String NO_COPY_RESERVABLE = "no_copy_reservable";
    String COULD_NOT_LOAD_DETAIL = "could_not_load_detail";
    String ERROR = "error";
    String DOWNLOAD = "download";
    String STATUS = "status_detail";
    String REMINDERS = "reminders";
    String PROLONGED_ABBR = "prolonged_abbr";
    String FREE_SEARCH = "free_search";
    String WRONG_LOGIN_DATA = "wrong_account_data";
    String PROLONGING_IMPOSSIBLE = "prolonging_impossible";
    String PROLONGING_EXPIRED = "prolonging_expired";
    String PROLONGING_WAITING = "prolonging_expired";
    String ORDER = "order";
    String ORDER_DEFAULT = "order_default";
    String ORDER_YEAR_ASC = "order_year_asc";
    String ORDER_CATEGORY_ASC = "order_category_asc";
    String ORDER_YEAR_DESC = "order_year_desc";
    String ORDER_CATEGORY_DESC = "order_category_desc";
    String NO_RESULTS = "no_results";
    String INTERLIB_BRANCH = "interlib_branch";
    String STACKS_BRANCH = "stacks_branch";
    String PROVISION_BRANCH = "provision_branch";
    String UNSUPPORTED_IN_LIBRARY = "unsupported_in_library";
    String RESERVATIONS_NUMBER = "reservations_number";
    String RESERVED_AT_DATE = "reserved_at_date";
    String LINK = "link";
    String FEE_CONFIRMATION = "fee_confirmation";
    String ALL = "all";
    String DESCRIPTION = "description";
    String RESERVATION_READY = "reservation_ready";
    String PLEASE_CHANGE_PASSWORD = "please_change_password";
    String PROLONG_ALL_NOT_POSSIBLE = "prolong_all_not_possible";
    String PROLONG_ALL_NO_ITEMS = "prolong_all_no_items";
    String HOLD = "hold";
    String REQUEST_READY = "request_ready";
    String READINGROOM = "readingroom";
    String REQUEST_PROGRESS = "request_progress";
    String RESERVED_POS = "reserved_pos";
    String ITEM_COPY = "item_copy";
    String RESERVED = "reserved";
    String RENEWED = "renewed";
    String NOT_YET_RENEWABLE = "not_yet_renewable";
    String RENEW_ILL_SEPARATELY = "renew_ill_separately";
    String INQUIRE_AVAILABILITY = "inquire_availability";

    /**
     * Returns the translated string identified by identifier
     *
     * @param identifier The ID of the string
     * @return the translated string
     */
    String getString(String identifier);

    /**
     * Returns a translated formatted string
     *
     * @param identifier The ID of the string
     * @param args       Formatting arguments
     * @return the translated and formatted string
     */
    String getFormattedString(String identifier, Object... args);

    /**
     * Returns a translated quantity string
     *
     * @param identifier The ID of the string
     * @param count      Number for determining the plural to use
     * @param args       Formatting arguments
     * @return the translated and formatted string
     */
    String getQuantityString(String identifier, int count, Object... args);

    /**
     * Returns the localized name of a media type
     *
     * @param mediaType the MediaType
     * @return the translated string
     */
    String getMediaTypeName(SearchResult.MediaType mediaType);

}
