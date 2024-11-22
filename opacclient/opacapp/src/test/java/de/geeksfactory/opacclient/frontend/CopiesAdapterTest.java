package de.geeksfactory.opacclient.frontend;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.Shadows;

import de.geeksfactory.opacclient.R;
import de.geeksfactory.opacclient.objects.Copy;
import de.geeksfactory.opacclient.objects.SearchResult;
import de.geeksfactory.opacclient.reminder.SyncAccountJob;
import de.geeksfactory.opacclient.utils.DebugTools;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class CopiesAdapterTest {
    private static MockedStatic<SyncAccountJob> mockSyncAccountJob;
    private static MockedStatic<DebugTools> mockDebugTools;
    private Context context;
    private CopiesAdapter adapter;

    @BeforeClass
    public static void setUpClass() {
        // avoid java.lang.IllegalStateException: WorkManager is not initialized properly.
        mockSyncAccountJob = Mockito.mockStatic(SyncAccountJob.class);
        // avoid java.io.IOException: socket not created
        mockDebugTools = Mockito.mockStatic(DebugTools.class);
    }

    @AfterClass
    public static void tearDownClass() {
        mockDebugTools.close();
        mockSyncAccountJob.close();
    }

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void onCreateViewHolder() {
        CopiesAdapter adapter = new CopiesAdapter(asList(new Copy()), context);
        MockedStatic<LayoutInflater> mockStaticLayoutInflater = Mockito.mockStatic(LayoutInflater.class);
        LayoutInflater mockLayoutInflater = mock(LayoutInflater.class);
        ViewGroup mockParent = mock(ViewGroup.class);
        View mockView = mock(View.class);

        when(LayoutInflater.from(context)).thenReturn(mockLayoutInflater);
        when(mockLayoutInflater.inflate(anyInt(), eq(mockParent), eq(false))).thenReturn(mockView);

        CopiesAdapter.ViewHolder holder = adapter.onCreateViewHolder(mockParent, 0);
        assertEquals(mockView, holder.itemView);

        mockStaticLayoutInflater.close();
    }

    @Test
    public void onBindViewHolder() {
        LocalDate date = new LocalDate("2020-12-31");
        Copy copy1 = new Copy();
        copy1.setLocation("location");
        copy1.setDepartment("department");
        copy1.setBranch("branch");
        copy1.setIssue("issue");
        copy1.setStatus("status");
        copy1.setStatusCode(SearchResult.Status.GREEN);
        copy1.setReturnDate(date);
        copy1.setReservations("reservations");
        copy1.setShelfmark("shelfmark");
        copy1.setUrl("url");
        Copy copy2 = new Copy();
        copy2.setStatusCode(SearchResult.Status.YELLOW);
        copy2.setLocation("");
        Copy copy3 = new Copy();
        copy3.setStatusCode(SearchResult.Status.RED);
        Copy copy4 = new Copy();
        copy4.setStatusCode(SearchResult.Status.UNKNOWN);
        Copy copy5 = new Copy();

        adapter = new CopiesAdapter(asList(copy1, copy2, copy3, copy4, copy5), context);
        LayoutInflater layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = layoutInflater.inflate(R.layout.listitem_copy, null, false);

        CopiesAdapter.ViewHolder holder0 = new CopiesAdapter.ViewHolder(view);
        adapter.onBindViewHolder(holder0, 0);
        assertEquals("location", holder0.tvLocation.getText().toString());
        assertEquals("department", holder0.tvDepartment.getText().toString());
        assertEquals("branch", holder0.tvBranch.getText().toString());
        assertEquals("issue", holder0.tvIssue.getText().toString());
        assertEquals("status", holder0.tvStatus.getText().toString());
        assertEquals(R.drawable.status_light_green_check, Shadows.shadowOf(holder0.tvStatus.getCompoundDrawables()[0]).getCreatedFromResId());
        assertEquals(DateTimeFormat.shortDate().print(date), holder0.tvReturndate.getText().toString());
        assertEquals("reservations", holder0.tvReservations.getText().toString());
        assertEquals("shelfmark", holder0.tvShelfmark.getText().toString());
        assertEquals("url", holder0.tvUrl.getText().toString());
        assertEquals(View.VISIBLE, holder0.tvLocation.getVisibility());

        CopiesAdapter.ViewHolder holder1 = new CopiesAdapter.ViewHolder(view);
        adapter.onBindViewHolder(holder1, 1);
        assertEquals(R.drawable.status_light_yellow_alert, Shadows.shadowOf(holder1.tvStatus.getCompoundDrawables()[0]).getCreatedFromResId());
        assertEquals(View.GONE, holder1.tvLocation.getVisibility());

        CopiesAdapter.ViewHolder holder2 = new CopiesAdapter.ViewHolder(view);
        adapter.onBindViewHolder(holder2, 2);
        assertEquals(R.drawable.status_light_red_cross, Shadows.shadowOf(holder2.tvStatus.getCompoundDrawables()[0]).getCreatedFromResId());
        assertEquals(View.GONE, holder2.tvLocation.getVisibility());
        assertEquals(View.GONE, holder2.tvReturndate.getVisibility());

        CopiesAdapter.ViewHolder holder3 = new CopiesAdapter.ViewHolder(view);
        adapter.onBindViewHolder(holder3, 3);
        assertEquals(R.drawable.ic_status_24dp, Shadows.shadowOf(holder3.tvStatus.getCompoundDrawables()[0]).getCreatedFromResId());

        CopiesAdapter.ViewHolder holder4 = new CopiesAdapter.ViewHolder(view);
        adapter.onBindViewHolder(holder4, 4);
        assertEquals(R.drawable.ic_status_24dp, Shadows.shadowOf(holder4.tvStatus.getCompoundDrawables()[0]).getCreatedFromResId());
    }

    @Test
    public void itemCount() {
        Copy copy = new Copy();
        CopiesAdapter adapter = new CopiesAdapter(asList(copy), context);
        assertEquals(1, adapter.getItemCount());
    }
}
