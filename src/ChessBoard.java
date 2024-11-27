import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Stack;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class ChessBoard extends JPanel {
    private final ChessPiece[][] board;  // 2D массив для хранения фигур на доске
    private ChessPiece selectedPiece;    // Выбранная для хода фигура
    private int selectedX, selectedY;    // Координаты выбранной фигуры
    private boolean whiteTurn = true;    // Очередность хода (true - белые, false - черные)
    private final List<Position> availableMoves = new ArrayList<>(); // Список доступных ходов
    private final List<Position> attackMoves = new ArrayList<>(); // Список возможных атак
    private final Stack<Move> moveHistory; // Список для хранения истории ходов
    private boolean isKingInCheck;
    private Position checkedKingPosition;
    private Timer blinkTimer;
    private boolean isBlinkOn;

    public ChessBoard() {
        // Устанавливаем компоновку для размещения компонентов
        setLayout(new BorderLayout());

        // Инициализируем поля для моргания
        isKingInCheck = false;
        checkedKingPosition = null;
        isBlinkOn = false;

        // Создаем панель для шахматной доски
        JPanel boardPanel = getjPanel();

        // Создаем панель управления
        JPanel controlPanel = new JPanel();
        JButton undoButton = new JButton("Отменить ход");
        undoButton.addActionListener(_ -> undoLastMove());
        controlPanel.add(undoButton);

        // Добавляем компоненты на главную панель
        add(boardPanel, BorderLayout.CENTER);
        add(controlPanel, BorderLayout.SOUTH);

        // Инициализируем доску и историю ходов
        board = new ChessPiece[8][8];
        moveHistory = new Stack<>();

        // Настраиваем таймер для моргания
        setupBlinkTimer();

        // Добавляем слушатель закрытия окна
        Frame frame = (Frame) SwingUtilities.getWindowAncestor(this);
        if (frame != null) {
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    if (blinkTimer != null && blinkTimer.isRunning()) {
                        blinkTimer.stop();
                    }
                }
            });
        }

        // Расставляем фигуры
        initializeBoard();
    }

    private JPanel getjPanel() {
        JPanel boardPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawBoard(g);
                drawPieces(g);
            }
        };
        boardPanel.setPreferredSize(new Dimension(640, 640));

        // Добавляем обработчик кликов мыши
        boardPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handleMouseClick(e.getX(), e.getY());
            }
        });
        return boardPanel;
    }

    // Инициализация шахматной доски и установка фигур (начальная расстановка фигур)
    private void initializeBoard() {
        // Установка пешек
        for (int i = 0; i < 8; i++) {
            board[1][i] = new Pawn(Color.BLACK);  // Черные пешки
            board[6][i] = new Pawn(Color.WHITE);  // Белые пешки
        }

        // Установка ладей
        board[0][0] = new Rook(Color.BLACK);
        board[0][7] = new Rook(Color.BLACK);
        board[7][0] = new Rook(Color.WHITE);
        board[7][7] = new Rook(Color.WHITE);

        // Установка коней
        board[0][1] = new Horse(Color.BLACK);
        board[0][6] = new Horse(Color.BLACK);
        board[7][1] = new Horse(Color.WHITE);
        board[7][6] = new Horse(Color.WHITE);

        // Установка слонов
        board[0][2] = new Bishop(Color.BLACK);
        board[0][5] = new Bishop(Color.BLACK);
        board[7][2] = new Bishop(Color.WHITE);
        board[7][5] = new Bishop(Color.WHITE);

        // Установка ферзей
        board[0][3] = new Queen(Color.BLACK);
        board[7][3] = new Queen(Color.WHITE);

        // Установка королей
        board[0][4] = new King(Color.BLACK);
        board[7][4] = new King(Color.WHITE);
    }

    // Обработка кликов мыши и ходов
    private void handleMouseClick(int x, int y) {
        int row = y / 80;
        int col = x / 80;

        if (selectedPiece == null) {
            if (board[row][col] != null && board[row][col].color == (whiteTurn ? Color.WHITE : Color.BLACK)) {
                selectedPiece = board[row][col];
                selectedX = row;
                selectedY = col;
                calculateAvailableMoves(row, col);
            }
        } else {
            Position targetPosition = new Position(row, col);
            boolean isValidTarget = availableMoves.stream().anyMatch(p -> p.equals(targetPosition)) ||
                    attackMoves.stream().anyMatch(p -> p.equals(targetPosition));

            if (!isValidTarget) {
                selectedPiece = null;
                availableMoves.clear();
                attackMoves.clear();
                repaint();
                return;
            }

            // Если ход разрешен, выполняем его
            Move move = new Move(selectedX, selectedY, row, col,
                    selectedPiece, board[row][col],
                    selectedPiece.hasMoved);

            if (selectedPiece instanceof King && Math.abs(col - selectedY) == 2) {
                if (canCastle(selectedX, selectedY, row, col)) {
                    handleCastling(row, col);
                    moveHistory.push(move);
                }
            } else if (selectedPiece instanceof Pawn && (row == 0 || row == 7)) {
                promotePawn(row, col);
                moveHistory.push(move);
            } else {
                board[row][col] = selectedPiece;
                board[selectedX][selectedY] = null;
                selectedPiece.hasMoved = true;
                moveHistory.push(move);
            }

            // Проверки на шах, мат и пат
            if (isInCheck(!whiteTurn ? Color.WHITE : Color.BLACK)) {
                isKingInCheck = true;
                checkedKingPosition = findKingPosition(!whiteTurn ? Color.WHITE : Color.BLACK);
                blinkTimer.start();
                if (isCheckmate(!whiteTurn ? Color.WHITE : Color.BLACK)) {
                    JOptionPane.showMessageDialog(this,
                            "Шах и Мат! " + (whiteTurn ? "Белые" : "Черные") + " победили!");
                    blinkTimer.stop();
                } else {
                    JOptionPane.showMessageDialog(this, "Шах!");
                }
            } else if (isStalemate(!whiteTurn ? Color.WHITE : Color.BLACK)) {
                JOptionPane.showMessageDialog(this, "Пат! Ничья!");
                blinkTimer.stop();
                isKingInCheck = false;
            } else {
                isKingInCheck = false;
                blinkTimer.stop();
            }

            whiteTurn = !whiteTurn;
            selectedPiece = null;
            availableMoves.clear();
            attackMoves.clear();
        }
        repaint();
    }

    // Расчет возможных ходов для выбранной фигуры
    private void calculateAvailableMoves(int startX, int startY) {
        availableMoves.clear();
        attackMoves.clear();

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                if (selectedPiece.isValidMove(startX, startY, row, col, board)) {
                    // Специальная проверка для рокировки
                    if (selectedPiece instanceof King && Math.abs(col - startY) == 2) {
                        if (canCastle(startX, startY, row, col)) {
                            availableMoves.add(new Position(row, col));
                        }
                    } else {
                        // Проверка для обычных ходов
                        ChessPiece tempPiece = board[row][col];
                        board[row][col] = selectedPiece;
                        board[startX][startY] = null;

                        boolean exposesKing = isInCheck(selectedPiece.color);

                        board[startX][startY] = selectedPiece;
                        board[row][col] = tempPiece;

                        if (!exposesKing) {
                            if (tempPiece == null) {
                                availableMoves.add(new Position(row, col));
                            } else if (tempPiece.color != selectedPiece.color) {
                                attackMoves.add(new Position(row, col));
                            }
                        }
                    }
                }
            }
        }
    }

    // Метод для проверки, спасает ли ход от шаха:
    private boolean moveResolvesCheck(int startX, int startY, int endX, int endY) {
        ChessPiece movingPiece = board[startX][startY];

        // Особая проверка для хода королем
        if (movingPiece instanceof King) {
            return isValidKingMove(startX, startY, endX, endY, movingPiece.color);
        }

        // Для остальных фигур - проверяем, спасает ли ход от шаха
        ChessPiece tempPiece = board[endX][endY];
        board[endX][endY] = movingPiece;
        board[startX][startY] = null;

        boolean stillInCheck = isInCheck(movingPiece.color);

        // Возвращаем фигуры на место
        board[startX][startY] = movingPiece;
        board[endX][endY] = tempPiece;

        return !stillInCheck;
    }

    private boolean isValidKingMove(int startX, int startY, int endX, int endY, Color kingColor) {
        // Проверяем границы доски
        if (endX < 0 || endX >= 8 || endY < 0 || endY >= 8) {
            return false;
        }

        // Временно перемещаем короля
        ChessPiece tempPiece = board[endX][endY];
        ChessPiece king = board[startX][startY];
        board[endX][endY] = king;
        board[startX][startY] = null;

        // Проверяем, не находится ли новая позиция под атакой
        boolean isSafe = !isSquareUnderAttack(endX, endY, kingColor == Color.WHITE ? Color.BLACK : Color.WHITE);

        // Возвращаем фигуры на место
        board[startX][startY] = king;
        board[endX][endY] = tempPiece;

        return isSafe;
    }

    // Проверка на рокировку и выполнение рокировки
    private void handleCastling(int endX, int endY) {
        boolean isKingSide = endY > selectedY; // Определяем тип рокировки (короткая или длинная)

        // Начальная позиция ладьи
        int rookStartCol = isKingSide ? 7 : 0;

        // Конечная позиция ладьи (за королем)
        int rookEndCol = isKingSide ? endY - 1 : endY + 1;

        // Перемещаем короля
        board[endX][endY] = selectedPiece;
        board[selectedX][selectedY] = null;
        selectedPiece.hasMoved = true;

        // Перемещаем ладью
        ChessPiece rook = board[endX][rookStartCol];
        board[endX][rookEndCol] = rook;
        board[endX][rookStartCol] = null;
        rook.hasMoved = true;
    }

    // Проверка возможности рокировки
    @SuppressWarnings("unused") // int endX возможно буду использовать в дальнейшем, пока не знаю
    private boolean canCastle(int startX, int startY, int endX, int endY) {
        // Проверяем, что это король и он не двигался
        if (!(selectedPiece instanceof King) || selectedPiece.hasMoved) return false;

        // Проверяем корректность начальной позиции короля
        boolean isCorrectStartPosition = (startX == 0 || startX == 7) && startY == 4;
        if (!isCorrectStartPosition) return false;

        // Проверяем, что король движется на две клетки
        if (Math.abs(endY - startY) != 2) return false;

        // Проверяем, что король не под шахом
        if (isInCheck(selectedPiece.color)) return false;

        // Определяем сторону рокировки (королевская/ферзевая)
        int rookCol = (endY > startY) ? 7 : 0;
        ChessPiece rook = board[startX][rookCol];

        // Проверяем наличие ладьи и что она не двигалась
        if (!(rook instanceof Rook) || rook.hasMoved) return false;

        // Проверяем отсутствие фигур между королем и ладьей
        int step = (endY > startY) ? 1 : -1;
        for (int col = startY + step; col != rookCol; col += step) {
            if (board[startX][col] != null) return false;
        }

        // Проверяем, что промежуточные клетки не под ударом
        Color opponentColor = selectedPiece.color == Color.WHITE ? Color.BLACK : Color.WHITE;
        for (int col = startY; col != endY + step; col += step) {
            if (isSquareUnderAttack(startX, col, opponentColor)) return false;
        }

        return true;
    }

    private boolean isSquareUnderAttack(int row, int col, Color attackerColor) {
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                ChessPiece piece = board[i][j];
                if (piece != null && piece.color == attackerColor) {
                    if (piece.isValidMove(i, j, row, col, board)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // Превращение пешки
    private void promotePawn(int row, int col) {
        String[] options = {"Ферзь", "Ладья", "Слон", "Конь"};
        int choice = JOptionPane.showOptionDialog(this, "Выберите фигуру для превращения:", "Превращение пешки",
                JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);
        if (choice != JOptionPane.CLOSED_OPTION) {
            ChessPiece newPiece = null;
            switch (choice) {
                case 0 -> newPiece = new Queen(selectedPiece.color);
                case 1 -> newPiece = new Rook(selectedPiece.color);
                case 2 -> newPiece = new Bishop(selectedPiece.color);
                case 3 -> newPiece = new Horse(selectedPiece.color);
            }
            board[row][col] = newPiece;  // Устанавливаем новую фигуру
            board[selectedX][selectedY] = null;  // Убираем пешку с начальной позиции
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        drawBoard(g);  // этот метод теперь включает логику подсветки
        drawPieces(g);
    }

    // Таймер для моргания клетки
    private void setupBlinkTimer() {
        blinkTimer = new Timer(500, _ -> {
            isBlinkOn = !isBlinkOn;
            repaint();
        });
    }

    // Рисование шахматной доски
    private void drawBoard(Graphics g) {
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                // Базовый цвет клетки
                if ((row + col) % 2 == 0) {
                    g.setColor(new Color(225, 198, 153));
                } else {
                    g.setColor(new Color(139, 69, 19));
                }
                g.fillRect(col * 80, row * 80, 80, 80);

                // Подсветка доступных ходов
                Position currentPos = new Position(row, col);
                if (availableMoves.stream().anyMatch(p -> p.equals(currentPos))) {
                    g.setColor(new Color(144, 238, 144, 150));
                    g.fillRect(col * 80, row * 80, 80, 80);
                }
                if (attackMoves.stream().anyMatch(p -> p.equals(currentPos))) {
                    g.setColor(new Color(255, 0, 0, 150));
                    g.fillRect(col * 80, row * 80, 80, 80);
                }
            }
        }

        // Отрисовка моргающей клетки короля
        if (isKingInCheck && checkedKingPosition != null && isBlinkOn) {
            g.setColor(new Color(255, 0, 0, 150));
            g.fillRect(checkedKingPosition.col * 80,
                    checkedKingPosition.row * 80, 80, 80);
        }
    }

    // Рисование фигур на доске
    private void drawPieces(Graphics g) {
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                if (board[row][col] != null) {
                    board[row][col].draw(g, col * 80, row * 80);
                }
            }
        }
    }

    // Метод для проверки, находится ли игрок под шахом
    private boolean isInCheck(Color color) {
        Position kingPosition = findKingPosition(color);
        if (kingPosition == null) return false;

        // Проверяем атаки от всех фигур противника
        Color opponentColor = (color == Color.WHITE) ? Color.BLACK : Color.WHITE;
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                ChessPiece piece = board[row][col];
                if (piece != null && piece.color == opponentColor) {
                    if (piece.isValidMove(row, col, kingPosition.row, kingPosition.col, board)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // Метод для поиска позиции короля
    private Position findKingPosition(Color color) {
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                if (board[row][col] instanceof King && board[row][col].color == color) {
                    return new Position(row, col);
                }
            }
        }
        return null;
    }

    // Метод для проверки, является ли текущая позиция пат
    private boolean isStalemate(Color color) {
        // Если король под шахом, это не пат
        if (isInCheck(color)) {
            return false;
        }

        // Проверяем все фигуры данного цвета на наличие возможных ходов
        for (int startRow = 0; startRow < 8; startRow++) {
            for (int startCol = 0; startCol < 8; startCol++) {
                ChessPiece piece = board[startRow][startCol];
                if (piece != null && piece.color == color) {
                    // Временно выбираем фигуру для проверки её ходов
                    selectedPiece = piece;
                    selectedX = startRow;
                    selectedY = startCol;
                    calculateAvailableMoves(startRow, startCol);

                    // Если у фигуры есть доступные ходы, это не пат
                    if (!availableMoves.isEmpty() || !attackMoves.isEmpty()) {
                        // Очищаем временные данные
                        selectedPiece = null;
                        availableMoves.clear();
                        attackMoves.clear();
                        return false;
                    }
                }
            }
        }

        // Если не найдено ни одного возможного хода, это пат
        return true;
    }

    // Метод для проверки, является ли текущая позиция мата
    private boolean isCheckmate(Color color) {
        // Проверяем наличие шаха
        if (!isInCheck(color)) return false;

        // Получаем позицию короля
        Position kingPosition = findKingPosition(color);

        // Проверяем все возможные ходы короля
        assert kingPosition != null;
        King king = (King) board[kingPosition.row][kingPosition.col];
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                int newRow = kingPosition.row + dx;
                int newCol = kingPosition.col + dy;

                if (newRow >= 0 && newRow < 8 && newCol >= 0 && newCol < 8) {
                    if (king.isValidMove(kingPosition.row, kingPosition.col, newRow, newCol, board) &&
                            moveResolvesCheck(kingPosition.row, kingPosition.col, newRow, newCol)) {
                        return false;
                    }
                }
            }
        }

        // Проверяем, может ли какая-либо фигура защитить короля
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                ChessPiece piece = board[row][col];
                if (piece != null && piece.color == color && !(piece instanceof King)) {
                    for (int endX = 0; endX < 8; endX++) {
                        for (int endY = 0; endY < 8; endY++) {
                            if (piece.isValidMove(row, col, endX, endY, board) &&
                                    moveResolvesCheck(row, col, endX, endY)) {
                                return false;
                            }
                        }
                    }
                }
            }
        }

        return true;
    }

    // Класс для хранения позиции на доске
    private static class Position {
        int row, col;

        Position(int row, int col) {
            this.row = row;
            this.col = col;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Position position = (Position) o;
            return row == position.row && col == position.col;
        }

        @Override
        public int hashCode() {
            return Objects.hash(row, col);
        }
    }

    // Класс для хранения информации о ходе
    private static class Move {
        int startX, startY, endX, endY;
        ChessPiece movedPiece;
        ChessPiece capturedPiece;
        boolean wasFirstMove;  // для пешек, короля и ладьи

        Move(int startX, int startY, int endX, int endY,
             ChessPiece movedPiece, ChessPiece capturedPiece, boolean wasFirstMove) {
            this.startX = startX;
            this.startY = startY;
            this.endX = endX;
            this.endY = endY;
            this.movedPiece = movedPiece;
            this.capturedPiece = capturedPiece;
            this.wasFirstMove = wasFirstMove;
        }
    }

    // Класс для отмены хода
    private void undoLastMove() {
        if (!moveHistory.isEmpty()) {
            Move lastMove = moveHistory.pop();

            // Возвращаем фигуру на исходную позицию
            board[lastMove.startX][lastMove.startY] = lastMove.movedPiece;
            board[lastMove.endX][lastMove.endY] = lastMove.capturedPiece;

            // Восстанавливаем состояние первого хода
            if (lastMove.movedPiece instanceof King ||
                    lastMove.movedPiece instanceof Rook ||
                    lastMove.movedPiece instanceof Pawn) {
                lastMove.movedPiece.hasMoved = lastMove.wasFirstMove;
            }

            // Сбрасываем состояние моргания
            isKingInCheck = false;
            checkedKingPosition = null;
            if (blinkTimer.isRunning()) {
                blinkTimer.stop();
            }

            // Меняем очередь хода
            whiteTurn = !whiteTurn;

            // Обновляем отображение
            repaint();
        }
    }

    // Абстрактный класс фигуры (базовый класс для всех фигур)
    abstract static class ChessPiece {
        Color color;
        protected boolean hasMoved = false; // Добавляем здесь

        ChessPiece(Color color) {
            this.color = color;
        }

        abstract void draw(Graphics g, int x, int y);
        abstract boolean isValidMove(int startX, int startY, int endX, int endY, ChessPiece[][] board);
    }

    // Реализация пешки
    static class Pawn extends ChessPiece {
        Pawn(Color color) {
            super(color);
        }

        @Override
        void draw(Graphics g, int x, int y) {
            g.setColor(color);
            g.fillOval(x + 20, y + 20, 40, 40);  // просто круг для пешки
        }

        @Override
        boolean isValidMove(int startX, int startY, int endX, int endY, ChessPiece[][] board) {
            int direction = (color == Color.WHITE) ? -1 : 1;
            if (board[endX][endY] == null) {
                // Пешка двигается на одну клетку вперед
                if (endX == startX + direction && startY == endY) {
                    return true;
                }
                // Пешка может двигаться на две клетки вперед, если она еще не ходила
                return endX == startX + 2 * direction && startY == endY
                        && (startX == 1 || startX == 6)
                        && board[startX + direction][startY] == null;
            } else {
                // Пешка атакует по диагонали
                return endX == startX + direction && (endY == startY - 1 || endY == startY + 1);
            }
        }
    }

    // Реализация ладьи
    static class Rook extends ChessPiece {
        Rook(Color color) {
            super(color);
        }

        @Override
        void draw(Graphics g, int x, int y) {
            g.setColor(color);
            g.fillRect(x + 10, y + 10, 60, 60);
        }

        @Override
        boolean isValidMove(int startX, int startY, int endX, int endY, ChessPiece[][] board) {
            // Базовая проверка корректности координат
            if (endX < 0 || endX >= 8 || endY < 0 || endY >= 8) {
                return false;
            }

            // Проверка, что ход выполняется по прямой линии
            if (startX != endX && startY != endY) {
                return false;
            }

            // Если ход по горизонтали
            if (startX == endX) {
                int start = Math.min(startY, endY);
                int end = Math.max(startY, endY);
                for (int y = start + 1; y < end; y++) {
                    if (board[startX][y] != null) {
                        return false;
                    }
                }
            }

            // Если ход по вертикали
            if (startY == endY) {
                int start = Math.min(startX, endX);
                int end = Math.max(startX, endX);
                for (int x = start + 1; x < end; x++) {
                    if (board[x][startY] != null) {
                        return false;
                    }
                }
            }

            // Проверка конечной позиции
            return board[endX][endY] == null || board[endX][endY].color != this.color;
        }
    }

    // Реализация коня
    static class Horse extends ChessPiece {
        Horse(Color color) {
            super(color);
        }

        @Override
        void draw(Graphics g, int x, int y) {
            g.setColor(color);
            g.fillRect(x + 20, y + 20, 40, 40);  // квадрат для коня
        }

        @Override
        boolean isValidMove(int startX, int startY, int endX, int endY, ChessPiece[][] board) {
            int dx = Math.abs(startX - endX);
            int dy = Math.abs(startY - endY);
            return (dx == 2 && dy == 1) || (dx == 1 && dy == 2);
        }
    }

    // Реализация слона
    static class Bishop extends ChessPiece {
        Bishop(Color color) {
            super(color);
        }

        @Override
        void draw(Graphics g, int x, int y) {
            g.setColor(color);
            // Создаем равносторонний треугольник
            int[] xPoints = {x + 40, x + 10, x + 70}; // центр основания и края
            int[] yPoints = {y + 10, y + 70, y + 70}; // вершина и основание
            g.fillPolygon(xPoints, yPoints, 3);
        }

        @Override
        boolean isValidMove(int startX, int startY, int endX, int endY, ChessPiece[][] board) {
            if (Math.abs(startX - endX) == Math.abs(startY - endY)) {
                int xStep = (endX > startX) ? 1 : -1;
                int yStep = (endY > startY) ? 1 : -1;
                for (int i = 1; i < Math.abs(startX - endX); i++) {
                    if (board[startX + i * xStep][startY + i * yStep] != null) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }
    }

    // Реализация ферзя
    static class Queen extends ChessPiece {
        Queen(Color color) {
            super(color);
        }

        @Override
        void draw(Graphics g, int x, int y) {
            g.setColor(color);
            // Треугольник для ферзя
            g.fillPolygon(new int[]{x + 40, x + 20, x + 60}, new int[]{y + 10, y + 70, y + 70}, 3);
        }

        @Override
        boolean isValidMove(int startX, int startY, int endX, int endY, ChessPiece[][] board) {
            return new Rook(color).isValidMove(startX, startY, endX, endY, board)
                    || new Bishop(color).isValidMove(startX, startY, endX, endY, board);
        }
    }

    // Реализация короля
    static class King extends ChessPiece {
        King(Color color) {
            super(color);
        }

        @Override
        void draw(Graphics g, int x, int y) {
            g.setColor(color);
            g.fillRoundRect(x + 10, y + 10, 60, 60, 20, 20);
        }

        @Override
        boolean isValidMove(int startX, int startY, int endX, int endY, ChessPiece[][] board) {
            int dx = Math.abs(startX - endX);
            int dy = Math.abs(startY - endY);

            // Стандартные ходы короля
            if (dx <= 1 && dy <= 1) {
                return true;
            }

            // Проверка возможности рокировки
            if (!hasMoved && dx == 0 && dy == 2) {
                // Проверяем наличие ладьи и отсутствие фигур между королем и ладьей
                if (endY > startY) { // Короткая рокировка
                    return board[startX][7] instanceof Rook && !board[startX][7].hasMoved &&
                            board[startX][5] == null && board[startX][6] == null;
                } else { // Длинная рокировка
                    return board[startX][0] instanceof Rook && !board[startX][0].hasMoved &&
                            board[startX][1] == null && board[startX][2] == null && board[startX][3] == null;
                }
            }
            return false;
        }
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Шахматы v03.00 (November 2024)");
        ChessBoard chessBoard = new ChessBoard();
        frame.add(chessBoard);
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }
}