package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.ui.*
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getDatabase(applicationContext)
        val repository = PaymentRepository(database.transactionDao(), database.profileDao())
        val factory = ViewModelFactory(repository)
        val viewModel = ViewModelProvider(this, factory)[PaymentViewModel::class.java]

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    MainApp(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MainApp(
    viewModel: PaymentViewModel,
    modifier: Modifier = Modifier
) {

    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val profile by viewModel.profileState.collectAsStateWithLifecycle()
    val transactions by viewModel.transactionsState.collectAsStateWithLifecycle()
    val isEditingProfile by viewModel.isEditingProfile.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Main Top Bar
            TopBar(
                profile = profile,
                onAvatarClick = {
                    profile?.let {
                        viewModel.startEditingProfile(it.name, it.upiId)
                    }
                },
                onResetClick = {
                    viewModel.resetApp()
                }
            )

            // Dynamic Content Pane based on selected tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        slideInHorizontally(initialOffsetX = { if (targetState > initialState) it else -it }) + fadeIn() with
                                slideOutHorizontally(targetOffsetX = { if (targetState > initialState) -it else it }) + fadeOut()
                    },
                    label = "tab_transition"
                ) { targetTab ->
                    when (targetTab) {
                        AppTab.DASHBOARD -> DashboardTab(
                            profile = profile,
                            transactions = transactions.take(4),
                            viewModel = viewModel
                        )
                        AppTab.SEND -> SendPaymentTab(
                            profile = profile,
                            viewModel = viewModel
                        )
                        AppTab.RECEIVE -> ReceivePaymentTab(
                            profile = profile,
                            viewModel = viewModel
                        )
                        AppTab.PASSBOOK -> PassbookTab(
                            profile = profile,
                            transactions = transactions,
                            viewModel = viewModel
                        )
                    }
                }
            }

            // Central Navigation Bar with clean pills
            BottomNavBar(
                currentTab = currentTab,
                onTabSelected = { viewModel.switchTab(it) }
            )
        }

        // Animated Scratch-Off Claim Screen wrapper
        val sendProgress by viewModel.sendProgressState.collectAsStateWithLifecycle()
        if (sendProgress is SendProgress.Success) {
            val successData = sendProgress as SendProgress.Success
            ScratchCardOverlay(
                successData = successData,
                onDismiss = { viewModel.dismissSendProgress() }
            )
        }

        // Edit Profile Dialog Overlay
        if (isEditingProfile) {
            EditProfileDialog(viewModel = viewModel)
        }
    }
}

@Composable
fun TopBar(
    profile: Profile?,
    onAvatarClick: () -> Unit,
    onResetClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable(onClick = onAvatarClick)
                    .testTag("profile_avatar_section")
            ) {
                // Multi-colored premium neon avatar badge
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(SkyBlue, MintNeon)
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = profile?.name?.firstOrNull()?.toString()?.uppercase() ?: "A",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = CarbonBg
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = profile?.name ?: "Loading...",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = profile?.upiId ?: "loading@upi",
                            fontSize = 11.sp,
                            color = TextGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Profile",
                            tint = SkyBlue,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // No KYC Secure Tag
                Box(
                    modifier = Modifier
                        .background(MintGlow, shape = RoundedCornerShape(20.dp))
                        .border(1.dp, MintNeon.copy(alpha = 0.5f), shape = RoundedCornerShape(20.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Secure",
                            tint = MintNeon,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "NO KYC",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MintNeon,
                            letterSpacing = 1.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Custom Reset Database Button to debug/re-test cashback
                IconButton(
                    onClick = onResetClick,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("reset_db_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset Account",
                        tint = TextGray
                    )
                }
            }
        }
    }
}

@Composable
fun BottomNavBar(
    currentTab: AppTab,
    onTabSelected: (AppTab) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 8.dp,
        windowInsets = WindowInsets.navigationBars
    ) {
        NavigationBarItem(
            selected = currentTab == AppTab.DASHBOARD,
            onClick = { onTabSelected(AppTab.DASHBOARD) },
            icon = {
                Icon(
                    imageVector = if (currentTab == AppTab.DASHBOARD) Icons.Default.Home else Icons.Outlined.Home,
                    contentDescription = "Dashboard"
                )
            },
            label = { Text("Home", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = CarbonBg,
                selectedTextColor = MintNeon,
                indicatorColor = MintNeon,
                unselectedIconColor = TextGray,
                unselectedTextColor = TextGray
            ),
            modifier = Modifier.testTag("nav_dashboard")
        )

        NavigationBarItem(
            selected = currentTab == AppTab.SEND,
            onClick = { onTabSelected(AppTab.SEND) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send Payment"
                )
            },
            label = { Text("Send Money", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = CarbonBg,
                selectedTextColor = SkyBlue,
                indicatorColor = SkyBlue,
                unselectedIconColor = TextGray,
                unselectedTextColor = TextGray
            ),
            modifier = Modifier.testTag("nav_send")
        )

        NavigationBarItem(
            selected = currentTab == AppTab.RECEIVE,
            onClick = { onTabSelected(AppTab.RECEIVE) },
            icon = {
                Icon(
                    imageVector = Icons.Default.QrCode,
                    contentDescription = "Receive QR"
                )
            },
            label = { Text("Receive QR", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = CarbonBg,
                selectedTextColor = MintNeon,
                indicatorColor = MintNeon,
                unselectedIconColor = TextGray,
                unselectedTextColor = TextGray
            ),
            modifier = Modifier.testTag("nav_receive")
        )

        NavigationBarItem(
            selected = currentTab == AppTab.PASSBOOK,
            onClick = { onTabSelected(AppTab.PASSBOOK) },
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                    contentDescription = "Passbook"
                )
            },
            label = { Text("Passbook", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = CarbonBg,
                selectedTextColor = SolarAmber,
                indicatorColor = SolarAmber,
                unselectedIconColor = TextGray,
                unselectedTextColor = TextGray
            ),
            modifier = Modifier.testTag("nav_passbook")
        )
    }
}

// ==========================================
// TAB 1: DASHBOARD
// ==========================================
@Composable
fun DashboardTab(
    profile: Profile?,
    transactions: List<Transaction>,
    viewModel: PaymentViewModel
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // High-fidelity Metallic Balance Card
        item {
            BalanceCard(profile = profile, viewModel = viewModel)
        }

        // Explicit No-KYC Shield USP Info Box
        item {
            NoKycSecurityBanner()
        }

        // Quick transfers grid row
        item {
            QuickTransfersRow(viewModel = viewModel)
        }

        // Recent transaction preview heading
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Payments",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )
                TextButton(
                    onClick = { viewModel.switchTab(AppTab.PASSBOOK) },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(text = "View All", color = SkyBlue, fontSize = 13.sp)
                }
            }
        }

        if (transactions.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CarbonSurface),
                    border = BorderStroke(1.dp, CarbonBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Payments,
                            contentDescription = "No payments",
                            tint = TextGray,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No payments completed yet",
                            color = TextWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Send ₹10 to quick contacts to test the instant cashback rewards!",
                            color = TextGray,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        } else {
            items(transactions) { tx ->
                TransactionRow(tx = tx)
            }
        }
    }
}

@Composable
fun BalanceCard(
    profile: Profile?,
    viewModel: PaymentViewModel
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = MintNeon
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CarbonSurface),
        border = BorderStroke(1.dp, CarbonBorder)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    // Draw neon technological grids in corner
                    val drawSize = this.size
                    val w = drawSize.width
                    val h = drawSize.height
                    drawCircle(
                        color = MintNeon.copy(alpha = 0.04f),
                        center = Offset(w * 0.9f, h * 0.2f),
                        radius = 200f
                    )
                    drawCircle(
                        color = SkyBlue.copy(alpha = 0.03f),
                        center = Offset(w * 0.1f, h * 0.8f),
                        radius = 150f
                    )
                }
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = "Wallet Icon",
                            tint = MintNeon,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AEROPAY DEPOSIT BALANCE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextGray,
                            letterSpacing = 1.sp
                        )
                    }
                    Text(
                        text = "VISA SIMULATED",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = SkyBlue,
                        letterSpacing = 0.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Beautiful structured Currency Display
                Row(
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = "₹",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Normal,
                        color = MintNeon,
                        modifier = Modifier.padding(bottom = 6.dp, end = 4.dp)
                    )
                    Text(
                        text = String.format(Locale.US, "%,.2f", profile?.balance ?: 0.00),
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Black,
                        color = TextWhite,
                        letterSpacing = (-0.5).sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Divider line
                HorizontalDivider(color = CarbonBorder, thickness = 1.dp)

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "TOTAL REWARDS CLAIMED",
                            fontSize = 9.sp,
                            color = TextGray,
                            letterSpacing = 0.5.sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stars,
                                contentDescription = "Cashback Gold",
                                tint = SolarAmber,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = String.format(Locale.US, "₹%,.2f", profile?.totalCashback ?: 0.0),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = SolarAmber
                            )
                        }
                    }

                    // Floating fast controls
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.switchTab(AppTab.SEND) },
                            colors = ButtonDefaults.buttonColors(containerColor = SkyBlue),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("fast_pay_button")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = "Pay",
                                    tint = CarbonBg,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Send",
                                    fontSize = 11.sp,
                                    color = CarbonBg,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Button(
                            onClick = { viewModel.switchTab(AppTab.RECEIVE) },
                            colors = ButtonDefaults.buttonColors(containerColor = MintGlow),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MintNeon.copy(alpha = 0.5f)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("fast_qr_button")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.QrCode,
                                    contentDescription = "QR",
                                    tint = MintNeon,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "My QR",
                                    fontSize = 11.sp,
                                    color = MintNeon,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NoKycSecurityBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CarbonCard),
        border = BorderStroke(1.dp, CarbonBorder)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .background(MintGlow, shape = CircleShape)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LockOpen,
                    contentDescription = "Verified No-KYC",
                    tint = MintNeon,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = "AeroPay Direct-Wallet Protocol (NO KYC)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = TextWhite
                )
                Text(
                    text = "Strictly zero identity papers, KYC audits, or facial checks required. Experience completely smooth peer-to-peer transfers with automated constant cashbacks credited on every transaction.",
                    fontSize = 11.sp,
                    color = TextGray,
                    modifier = Modifier.padding(top = 4.dp),
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
fun QuickTransfersRow(viewModel: PaymentViewModel) {
    data class QuickContact(val name: String, val upi: String, val defaultAmt: String, val avatarBg: Color)

    val contacts = listOf(
        QuickContact("Ram Chai", "ram.chai@upi", "40", Color(0xFFD4E157)),
        QuickContact("Taxi Depot", "metro.cab@upi", "250", Color(0xFF26A69A)),
        QuickContact("Amit Friend", "amit@upi", "1000", Color(0xFFAB47BC)),
        QuickContact("Juice Stall", "priya.juice@upi", "80", Color(0xFFEC407A))
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Tap to Instant Pay (Simulator Quick contacts)",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = TextWhite,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            contacts.forEach { contact ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable {
                            viewModel.prepopulateSend(contact.upi, contact.name, contact.defaultAmt)
                        }
                        .padding(4.dp)
                        .testTag("quick_contact_${contact.name.lowercase().replace(" ", "_")}")
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(contact.avatarBg.copy(alpha = 0.15f), shape = CircleShape)
                            .border(1.2.dp, contact.avatarBg.copy(alpha = 0.5f), shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = contact.name.take(1),
                            color = contact.avatarBg,
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = contact.name,
                        fontSize = 11.sp,
                        color = TextWhite,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                    Text(
                        text = "₹${contact.defaultAmt}",
                        fontSize = 10.sp,
                        color = MintNeon,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ==========================================
// TAB 2: SEND PAYMENT
// ==========================================
@Composable
fun SendPaymentTab(
    profile: Profile?,
    viewModel: PaymentViewModel
) {
    val upiInput by viewModel.sendUpiInput.collectAsStateWithLifecycle()
    val nameInput by viewModel.sendNameInput.collectAsStateWithLifecycle()
    val amountInput by viewModel.sendAmountInput.collectAsStateWithLifecycle()
    val noteInput by viewModel.sendNoteInput.collectAsStateWithLifecycle()
    val sendProgress by viewModel.sendProgressState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Transfer to UPI Destination",
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            color = TextWhite
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CarbonSurface),
            border = BorderStroke(1.dp, CarbonBorder)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Input Destination UPI
                OutlinedTextField(
                    value = upiInput,
                    onValueChange = { viewModel.sendUpiInput.value = it },
                    label = { Text("Destination UPI ID / Phone", color = TextGray) },
                    placeholder = { Text("receiver@upi or 9876543210", color = TextGray.copy(alpha = 0.5f)) },
                    leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = "UPI", tint = SkyBlue) },
                    trailingIcon = {
                        if (upiInput.isNotBlank()) {
                            IconButton(onClick = { viewModel.sendUpiInput.value = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextGray)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("send_upi_input"),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = SkyBlue,
                        unfocusedBorderColor = CarbonBorder
                    ),
                    singleLine = true
                )

                // Input Recipient name
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { viewModel.sendNameInput.value = it },
                    label = { Text("Recipient Display Name (Optional)", color = TextGray) },
                    placeholder = { Text("Merchant or Friend name", color = TextGray.copy(alpha = 0.5f)) },
                    leadingIcon = { Icon(Icons.Default.PersonOutline, contentDescription = "Name", tint = TextGray) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("send_name_input"),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = SkyBlue,
                        unfocusedBorderColor = CarbonBorder
                    ),
                    singleLine = true
                )

                // Input Amount
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { viewModel.sendAmountInput.value = it },
                    label = { Text("Enter Amount (₹)", color = TextGray) },
                    placeholder = { Text("0.00", color = TextGray.copy(alpha = 0.5f)) },
                    leadingIcon = { Icon(Icons.Default.CurrencyExchange, contentDescription = "Amount", tint = MintNeon) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("send_amount_input"),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = MintNeon,
                        unfocusedBorderColor = CarbonBorder
                    ),
                    singleLine = true
                )

                // Fast Amount Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("10", "50", "100", "500", "1000").forEach { amount ->
                        SuggestionChip(
                            onClick = {
                                val current = amountInput.toDoubleOrNull() ?: 0.0
                                val extra = amount.toDouble()
                                viewModel.sendAmountInput.value = (current + extra).toInt().toString()
                            },
                            label = { Text("+₹$amount", color = MintNeon, fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                            colors = SuggestionChipDefaults.suggestionChipColors(containerColor = CarbonCard),
                            border = BorderStroke(1.dp, CarbonBorder)
                        )
                    }
                }

                // Input Optional Notes
                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { viewModel.sendNoteInput.value = it },
                    label = { Text("Memo / Note (Optional)", color = TextGray) },
                    placeholder = { Text("E.g. Food, rent, tea stall payout", color = TextGray.copy(alpha = 0.5f)) },
                    leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = "Notes", tint = TextGray) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("send_note_input"),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = SkyBlue,
                        unfocusedBorderColor = CarbonBorder
                    ),
                    singleLine = true
                )
            }
        }

        // Available Wallet balance preview
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Available Balance:",
                fontSize = 12.sp,
                color = TextGray
            )
            Text(
                text = String.format(Locale.US, "₹%,.2f", profile?.balance ?: 0.0),
                fontSize = 13.sp,
                color = MintNeon,
                fontWeight = FontWeight.Bold
            )
        }

        // Warning alerts or message boxes
        if (sendProgress is SendProgress.Failed) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = AlertRed.copy(alpha = 0.15f)),
                border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = "Error", tint = AlertRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = (sendProgress as SendProgress.Failed).message,
                        color = TextWhite,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Main Submit Action Button
        Button(
            onClick = { viewModel.executeSendPayment() },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .shadow(4.dp, RoundedCornerShape(12.dp))
                .testTag("pay_submit_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = SkyBlue,
                disabledContainerColor = TextGray
            ),
            shape = RoundedCornerShape(12.dp),
            enabled = sendProgress !is SendProgress.Processing
        ) {
            if (sendProgress is SendProgress.Processing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = CarbonBg,
                    strokeWidth = 2.5.dp
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = "Transfer",
                        tint = CarbonBg
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AUTHORIZE PAY (NO KYC)",
                        fontSize = 14.sp,
                        color = CarbonBg,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

// ==========================================
// TAB 3: RECEIVE PAYMENT
// ==========================================
@Composable
fun ReceivePaymentTab(
    profile: Profile?,
    viewModel: PaymentViewModel
) {
    val receiveAmtInput by viewModel.receiveAmountInput.collectAsStateWithLifecycle()
    val senderName by viewModel.receiveSenderNameInput.collectAsStateWithLifecycle()
    val senderUpi by viewModel.receiveSenderUpiInput.collectAsStateWithLifecycle()
    val successText by viewModel.receiveSuccessMessage.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Your Personal Receive QR",
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            color = TextWhite
        )

        // Simulated QR Code graphic card
        Card(
            modifier = Modifier
                .width(280.dp)
                .shadow(6.dp, RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, CarbonBorder.copy(alpha = 0.15f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Top tagline
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .background(MintNeon, shape = CircleShape)
                            .size(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Ready", tint = Color.White, modifier = Modifier.size(8.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "AEROPAY SECURE QR",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black.copy(alpha = 0.6f),
                        letterSpacing = 0.5.sp
                    )
                }

                // Main simulated canvas QR draws grid-boxes reactively
                val qrSeed = "${profile?.upiId ?: "anon.pay@upi"}:${receiveAmtInput.ifBlank { "0" }}"
                SimulatedQRCode(
                    modifier = Modifier
                        .size(180.dp)
                        .background(Color.White),
                    text = qrSeed
                )

                // QR details
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = profile?.name ?: "User",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Text(
                        text = profile?.upiId ?: "loading@upi",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                if (receiveAmtInput.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .background(MintGlow, shape = RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "REQUESTING: ₹${receiveAmtInput}",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            color = MintNeon
                        )
                    }
                }
            }
        }

        // Simulating incoming deposit controls
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CarbonSurface),
            border = BorderStroke(1.dp, CarbonBorder)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Incoming Payment Simulator",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )

                Text(
                    text = "Simulate receiving funds from a merchant client or scanner. Enter amount and sender names down below, and press credit button to test your wallet transaction feed.",
                    fontSize = 11.sp,
                    color = TextGray,
                    lineHeight = 15.sp
                )

                // Input Sim Amount
                OutlinedTextField(
                    value = receiveAmtInput,
                    onValueChange = { viewModel.receiveAmountInput.value = it },
                    label = { Text("Simulated Credit Amount (₹)", color = TextGray) },
                    placeholder = { Text("0.00", color = TextGray.copy(alpha = 0.5f)) },
                    leadingIcon = { Icon(Icons.Default.AddCard, contentDescription = "Amount", tint = MintNeon) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("receive_amount_input"),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = MintNeon,
                        unfocusedBorderColor = CarbonBorder
                    ),
                    singleLine = true
                )

                // Row of input options (Names)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = senderName,
                        onValueChange = { viewModel.receiveSenderNameInput.value = it },
                        label = { Text("Sim Payer", color = TextGray, fontSize = 11.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("receive_payer_name_input"),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedBorderColor = SkyBlue,
                            unfocusedBorderColor = CarbonBorder
                        ),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = senderUpi,
                        onValueChange = { viewModel.receiveSenderUpiInput.value = it },
                        label = { Text("Payer UPI", color = TextGray, fontSize = 11.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("receive_payer_upi_input"),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedBorderColor = SkyBlue,
                            unfocusedBorderColor = CarbonBorder
                        ),
                        singleLine = true
                    )
                }

                // Credit button
                Button(
                    onClick = { viewModel.executeSimulateReceive() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("receive_submit_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MintNeon),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoveToInbox,
                        contentDescription = "Simulate Debit",
                        tint = CarbonBg
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SIMULATE INCOMING DEPOSIT",
                        fontWeight = FontWeight.Bold,
                        color = CarbonBg,
                        fontSize = 12.sp
                    )
                }
            }
        }

        if (successText != null) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissReceiveMessage() },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissReceiveMessage() }) {
                        Text("Awesome", color = MintNeon)
                    }
                },
                title = { Text("Funds Simulated!", fontWeight = FontWeight.Bold, color = TextWhite) },
                text = { Text(successText ?: "") },
                containerColor = CarbonSurface,
                titleContentColor = TextWhite,
                textContentColor = TextWhite
            )
        }
    }
}

// Custom simulated QR design drawing using Canvas
@Composable
fun SimulatedQRCode(
    modifier: Modifier = Modifier,
    text: String = ""
) {
    val matrixSize = 25
    Canvas(modifier = modifier) {
        val cellWidth = size.width / matrixSize
        val cellHeight = size.height / matrixSize

        // Clear white background drawing
        drawRect(color = Color.White, size = size)

        // Function draws 7x7 outer-ring locator zones
        fun drawLocator(x: Int, y: Int) {
            // White background spacer
            drawRect(
                color = Color.Black,
                topLeft = Offset(x * cellWidth, y * cellHeight),
                size = Size(7 * cellWidth, 7 * cellHeight)
            )
            drawRect(
                color = Color.White,
                topLeft = Offset((x + 1) * cellWidth, (y + 1) * cellHeight),
                size = Size(5 * cellWidth, 5 * cellHeight)
            )
            drawRect(
                color = Color.Black,
                topLeft = Offset((x + 2) * cellWidth, (y + 2) * cellHeight),
                size = Size(3 * cellWidth, 3 * cellHeight)
            )
        }

        // 3 Corner Locators
        drawLocator(0, 0) // Top Left
        drawLocator(matrixSize - 7, 0) // Top Right
        drawLocator(0, matrixSize - 7) // Bottom Left

        // Draw dynamic random matrix dots seeded on QR inputs.
        // Seeding with string hash means same UPI state yields same beautiful QR!
        val random = Random(text.hashCode().toLong())
        for (r in 0 until matrixSize) {
            for (c in 0 until matrixSize) {
                // Ignore overlapping locator zones
                if ((r < 8 && c < 8) || (r < 8 && c > matrixSize - 9) || (r > matrixSize - 9 && c < 8)) {
                    continue
                }
                // Random deterministic fill
                if (random.nextBoolean()) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(c * cellWidth, r * cellHeight),
                        size = Size(cellWidth, cellHeight)
                    )
                }
            }
        }
    }
}

// ==========================================
// TAB 4: PASSBOOK (TRANSACTIONS & CASHBACKS HISTORY)
// ==========================================
@Composable
fun PassbookTab(
    profile: Profile?,
    transactions: List<Transaction>,
    viewModel: PaymentViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Passbook & Rewards Activity",
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            color = TextWhite
        )

        // Passbook aggregation metrics card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CarbonSurface),
            border = BorderStroke(1.dp, CarbonBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("LEDGER TRANSACTIONS", fontSize = 9.sp, color = TextGray, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                    Text("${transactions.size} records", fontSize = 16.sp, fontWeight = FontWeight.Black, color = TextWhite)
                }
                Box(
                    modifier = Modifier
                        .size(1.dp, 36.dp)
                        .background(CarbonBorder)
                )
                Column {
                    Text("TOTAL EARNED REWARDS", fontSize = 9.sp, color = TextGray, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Stars, contentDescription = "Stars", tint = SolarAmber, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = String.format(Locale.US, "₹%,.2f", profile?.totalCashback ?: 0.0),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = SolarAmber
                        )
                    }
                }
            }
        }

        // Transaction Ledger feed
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            if (transactions.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Receipt,
                            contentDescription = "Empty Ledger",
                            tint = TextGray,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("No recorded logs", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            text = "Send simulated payments to enjoy cashbacks between 2% and 10% instant yields.",
                            color = TextGray,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp)
                        )
                    }
                }
            } else {
                items(transactions) { tx ->
                    TransactionRow(tx = tx)
                }
            }
        }
    }
}

@Composable
fun TransactionRow(tx: Transaction) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CarbonSurface),
        border = BorderStroke(1.dp, CarbonBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Circle arrow indicator
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            color = if (tx.isIncoming) MintGlow else SkyBlue.copy(alpha = 0.1f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (tx.isIncoming) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                        contentDescription = if (tx.isIncoming) "Incoming" else "Outgoing",
                        tint = if (tx.isIncoming) MintNeon else SkyBlue,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = tx.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = TextWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (tx.cashbackEarned > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .background(GoldGlow, shape = RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "🎁 +₹${tx.cashbackEarned}",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SolarAmber
                                )
                            }
                        }
                    }

                    Text(
                        text = tx.upiId,
                        fontSize = 11.sp,
                        color = TextGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Optional short memos
                    if (tx.note.isNotBlank()) {
                        Text(
                            text = "✏️ ${tx.note}",
                            fontSize = 10.sp,
                            color = TextGray.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Light,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            // Financial amounts alignment
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                val sign = if (tx.isIncoming) "+" else "-"
                val textColor = if (tx.isIncoming) MintNeon else TextWhite
                Text(
                    text = "$sign ₹${String.format(Locale.US, "%.2f", tx.amount)}",
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    color = textColor
                )

                // Fancy timestamp mapping
                val date = Date(tx.timestamp)
                val format = SimpleDateFormat("HH:mm, dd MMM", Locale.US)
                Text(
                    text = format.format(date),
                    fontSize = 9.sp,
                    color = TextGray
                )
            }
        }
    }
}

// ==========================================
// POPUPS / OVERLAY SHEETS
// ==========================================

@Composable
fun EditProfileDialog(
    viewModel: PaymentViewModel
) {
    val editedName by viewModel.editNameInput.collectAsStateWithLifecycle()
    val editedUpi by viewModel.editUpiIdInput.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = { viewModel.cancelEditingProfile() },
        title = {
            Text(
                text = "Edit Direct Wallet Details",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = TextWhite
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Provide personalized names and mock address codes with zero papers verified.",
                    fontSize = 11.sp,
                    color = TextGray
                )

                OutlinedTextField(
                    value = editedName,
                    onValueChange = { viewModel.editNameInput.value = it },
                    label = { Text("Display Name") },
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = SkyBlue,
                        unfocusedBorderColor = CarbonBorder
                    ),
                    singleLine = true,
                    modifier = Modifier.testTag("edit_profile_name")
                )

                OutlinedTextField(
                    value = editedUpi,
                    onValueChange = { viewModel.editUpiIdInput.value = it },
                    label = { Text("UPI custom alias") },
                    placeholder = { Text("youralias") },
                    suffix = { Text("@upi", color = TextGray) },
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = SkyBlue,
                        unfocusedBorderColor = CarbonBorder
                    ),
                    singleLine = true,
                    modifier = Modifier.testTag("edit_profile_upi")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.saveProfile() },
                colors = ButtonDefaults.buttonColors(containerColor = MintNeon),
                modifier = Modifier.testTag("edit_profile_save")
            ) {
                Text("Save Profile", color = CarbonBg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = { viewModel.cancelEditingProfile() },
                modifier = Modifier.testTag("edit_profile_cancel")
            ) {
                Text("Cancel", color = TextGray)
            }
        },
        containerColor = CarbonSurface,
        titleContentColor = TextWhite,
        textContentColor = TextWhite
    )
}

// Ultimate Scratch Card animation overlay for pure yield gratification!
@Composable
fun ScratchCardOverlay(
    successData: SendProgress.Success,
    onDismiss: () -> Unit
) {
    var isScratched by remember { mutableStateOf(false) }

    // Floating particles or spin indicator
    val infiniteTransition = rememberInfiniteTransition(label = "cashback_glow")
    val alphaGlow by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_tween"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(onClick = {}) // Absorb outer touches
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .shadow(12.dp, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = CarbonSurface),
            border = BorderStroke(2.dp, if (isScratched) SolarAmber else SkyBlue)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Success visual Check
                Box(
                    modifier = Modifier
                        .background(MintGlow, shape = CircleShape)
                        .padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Payout Success",
                        tint = MintNeon,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Text(
                    text = "TRANSACTION AUTHORIZED",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = SkyBlue,
                    letterSpacing = 1.5.sp
                )

                Text(
                    text = "Paid ₹${String.format(Locale.US, "%.2f", successData.amountPaid)} to ${successData.receiverName}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                HorizontalDivider(color = CarbonBorder, thickness = 1.dp)

                Spacer(modifier = Modifier.height(4.dp))

                AnimatedVisibility(
                    visible = !isScratched,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "🎁 AeroPay Cash Scratch Reward!",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            color = SolarAmber
                        )

                        Text(
                            text = "Every transaction yields instant cash scratch winnings. Tap reveal to claim your guaranteed cashback directly back into your AeroPay deposit balance.",
                            fontSize = 11.sp,
                            color = TextGray,
                            textAlign = TextAlign.Center,
                            lineHeight = 15.sp
                        )

                        Button(
                            onClick = { isScratched = true },
                            colors = ButtonDefaults.buttonColors(containerColor = SolarAmber),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("reveal_cashback_button")
                        ) {
                            Text(
                                "REVEAL GUARANTEED CASHBACK",
                                color = CarbonBg,
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = isScratched,
                    enter = fadeIn() + expandVertically()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "🎊 CASH WINNINGS CLAIMED! 🎊",
                            fontSize = 12.sp,
                            color = MintNeon,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )

                        Text(
                            text = "₹${String.format(Locale.US, "%.2f", successData.cashbackEarned)}",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            color = SolarAmber,
                            modifier = Modifier.shadow(10.dp, CircleShape, spotColor = SolarAmber)
                        )

                        Text(
                            text = "Instantly credited to your AeroPay Wallet.",
                            fontSize = 11.sp,
                            color = TextGray,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = CarbonBorder),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .testTag("scratch_dismiss_button")
                        ) {
                            Text("Done", color = TextWhite, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
