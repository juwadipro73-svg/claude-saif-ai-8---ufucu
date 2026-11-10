import 'package:flutter/material.dart';
import 'screens/scientific_screen.dart';
import 'screens/converter_screen.dart';
import 'screens/financial_screen.dart';
import 'screens/history_screen.dart';
import 'models/history_item.dart';

void main() {
  runApp(const SaifCalculatorApp());
}

class SaifCalculatorApp extends StatelessWidget {
  const SaifCalculatorApp({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Saif Advance Calculator',
      debugShowCheckedModeBanner: false,
      themeMode: ThemeMode.dark,
      darkTheme: ThemeData(
        useMaterial3: true,
        brightness: Brightness.dark,
        scaffoldBackgroundColor: const Color(0xFF0F172A),
        cardColor: const Color(0xFF1E293B),
        colorScheme: const ColorScheme.dark(
          primary: Color(0xFF0EA5E9),
          secondary: Color(0xFF10B981),
          surface: Color(0xFF1E293B),
        ),
      ),
      home: const MainNavigationScreen(),
    );
  }
}

class MainNavigationScreen extends StatefulWidget {
  const MainNavigationScreen({Key? key}) : super(key: key);

  @override
  State<MainNavigationScreen> createState() => _MainNavigationScreenState();
}

class _MainNavigationScreenState extends State<MainNavigationScreen> {
  int _currentIndex = 0;
  final List<HistoryItem> _history = [];

  void _addHistory(HistoryItem item) {
    setState(() {
      _history.insert(0, item);
      if (_history.length > 50) _history.removeLast();
    });
  }

  void _clearHistory() {
    setState(() {
      _history.clear();
    });
  }

  @override
  Widget build(BuildContext context) {
    final List<Widget> screens = [
      ScientificScreen(onNewHistory: _addHistory),
      const ConverterScreen(),
      const FinancialScreen(),
      HistoryScreen(history: _history, onClear: _clearHistory),
    ];

    return Scaffold(
      appBar: AppBar(
        title: const Row(
          children: [
            Text('⚡ ', style: TextStyle(fontSize: 20)),
            Text('Saif ProCalc', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18)),
          ],
        ),
        backgroundColor: const Color(0xFF1E293B),
        elevation: 0,
      ),
      body: SafeArea(
        child: IndexedStack(
          index: _currentIndex,
          children: screens,
        ),
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _currentIndex,
        onDestinationSelected: (idx) => setState(() => _currentIndex = idx),
        backgroundColor: const Color(0xFF1E293B),
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.calculate_outlined),
            selectedIcon: Icon(Icons.calculate, color: Color(0xFF0EA5E9)),
            label: 'Scientific',
          ),
          NavigationDestination(
            icon: Icon(Icons.swap_horiz_outlined),
            selectedIcon: Icon(Icons.swap_horiz, color: Color(0xFF0EA5E9)),
            label: 'Converter',
          ),
          NavigationDestination(
            icon: Icon(Icons.account_balance_outlined),
            selectedIcon: Icon(Icons.account_balance, color: Color(0xFF0EA5E9)),
            label: 'Finance',
          ),
          NavigationDestination(
            icon: Icon(Icons.history_outlined),
            selectedIcon: Icon(Icons.history, color: Color(0xFF0EA5E9)),
            label: 'History',
          ),
        ],
      ),
    );
  }
}
