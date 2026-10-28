package com.bagreturn;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import org.hibernate.Session;
import org.hibernate.Transaction;

import com.bagreturn.model.Customer;
import com.bagreturn.model.CustomerOrder;
import com.bagreturn.model.DepositContainer;
import com.bagreturn.model.OrderItem;
import com.bagreturn.model.Product;
import com.bagreturn.util.HibernateUtil;


public class Main {

    static Scanner sc = new Scanner(System.in);
    static DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    
    static final double DEPOSIT_RATE = 0.10;

    public static void main(String[] args) {
        try {
            addSampleProducts();
            System.out.println("=== Bag Return & Deposit Refund System ===");

            boolean running = true;
            while (running) {
                System.out.println();
                System.out.println("1. View products");
                System.out.println("2. Place order (10% container deposit)");
                System.out.println("3. Return container (refund deposit)");
                System.out.println("4. View all orders");
                System.out.println("5. View pending containers");
                System.out.println("6. Add product");
                System.out.println("7. View deposit & refund summary");
                System.out.println("0. Exit");
                System.out.print("Choose: ");
                String choice = sc.nextLine().trim();

                try {
                    if (choice.equals("1")) {
                        showProducts();
                    } else if (choice.equals("2")) {
                        placeOrder();
                    } else if (choice.equals("3")) {
                        returnContainer();
                    } else if (choice.equals("4")) {
                        showAllOrders();
                    } else if (choice.equals("5")) {
                        showPendingContainers();
                    } else if (choice.equals("6")) {
                        addProduct();
                    } else if (choice.equals("7")) {
                        showSummary();
                    } else if (choice.equals("0")) {
                        running = false;
                    } else {
                        System.out.println("Invalid option.");
                    }
                } catch (NumberFormatException e) {
                    System.out.println("!! Please enter a valid number.");
                } catch (IllegalArgumentException | IllegalStateException e) {
                    System.out.println("!! " + e.getMessage());
                } catch (Exception e) {
                    System.out.println("!! Error: " + e.getMessage());
                }
            }
        } finally {
            HibernateUtil.shutdown();
            System.out.println("Bye!");
        }
    }

    // ------------------------------------------------------------
    // 1. PRODUCTS
    // ------------------------------------------------------------

    // adds 6 sample products only when the products table is empty
    static void addSampleProducts() {
        Session session = HibernateUtil.getSessionFactory().openSession();
        Transaction tx = session.beginTransaction();
        try {
            Long count = session.createQuery("select count(p) from Product p", Long.class).uniqueResult();
            if (count == 0) {
                session.save(new Product("Veg Meals", 120));
                session.save(new Product("Chicken Biryani", 220));
                session.save(new Product("Veg Biryani", 160));
                session.save(new Product("Paneer Butter Masala", 180));
                session.save(new Product("Fried Rice", 130));
                session.save(new Product("Curd Rice", 80));
            }
            tx.commit();
        } catch (RuntimeException e) {
            tx.rollback();
            throw e;
        } finally {
            session.close();
        }
    }

    static void showProducts() {
        Session session = HibernateUtil.getSessionFactory().openSession();
        try {
            List<Product> products = session.createQuery("from Product order by id", Product.class).list();
            System.out.println();
            System.out.println("ID  | Product                | Price");
            System.out.println("----+------------------------+--------");
            for (Product p : products) {
                System.out.printf("%-3d | %-22s | %7.2f%n", p.getId(), p.getName(), p.getPrice());
            }
        } finally {
            session.close();
        }
    }

    static void addProduct() {
        System.out.print("Product name: ");
        String name = sc.nextLine().trim();
        System.out.print("Price: ");
        double price = Double.parseDouble(sc.nextLine().trim());

        if (name.isEmpty()) {
            throw new IllegalArgumentException("Product name is required");
        }
        if (price <= 0) {
            throw new IllegalArgumentException("Price must be greater than 0");
        }

        Session session = HibernateUtil.getSessionFactory().openSession();
        Transaction tx = session.beginTransaction();
        try {
            Product product = new Product(name, price);
            session.save(product);
            tx.commit();
            System.out.println("Product added with ID " + product.getId());
        } catch (RuntimeException e) {
            tx.rollback();
            throw e;
        } finally {
            session.close();
        }
    }

    // true if a product with this id exists
    static boolean productExists(int id) {
        Session session = HibernateUtil.getSessionFactory().openSession();
        try {
            return session.get(Product.class, id) != null;
        } finally {
            session.close();
        }
    }

    // ------------------------------------------------------------
    // 2. PLACE ORDER
    // ------------------------------------------------------------

    static void placeOrder() {
        showProducts();

        // cart = two lists: product ids and their quantities
        List<Integer> productIds = new ArrayList<>();
        List<Integer> quantities = new ArrayList<>();
        while (true) {
            System.out.print("Product ID (0 = done): ");
            int id = Integer.parseInt(sc.nextLine().trim());
            if (id == 0) {
                break;
            }
            if (!productExists(id)) {
                System.out.println("!! Invalid product id, try again.");
                continue;
            }
            System.out.print("Quantity: ");
            int qty = Integer.parseInt(sc.nextLine().trim());
            if (qty <= 0) {
                System.out.println("!! Quantity must be greater than 0, try again.");
                continue;
            }
            productIds.add(id);
            quantities.add(qty);
        }

        System.out.print("Customer name: ");
        String name = sc.nextLine();
        System.out.print("Phone (10 digits): ");
        String phone = sc.nextLine().trim();
        System.out.print("Delivery address: ");
        String address = sc.nextLine();

        // channel: keep asking until 1 or 2 is entered
        String channel = "";
        while (channel.equals("")) {
            System.out.print("Channel (1 = ONLINE, 2 = OFFLINE): ");
            String c = sc.nextLine().trim();
            if (c.equals("1")) {
                channel = "ONLINE";
            } else if (c.equals("2")) {
                channel = "OFFLINE";
            } else {
                System.out.println("!! Enter 1 or 2.");
            }
        }

        CustomerOrder order = saveOrder(name, phone, address, channel, productIds, quantities);
        System.out.println();
        System.out.println("Order placed successfully!");
        printOrder(order);
    }

   
    static CustomerOrder saveOrder(String name, String phone, String address, String channel,
                                   List<Integer> productIds, List<Integer> quantities) {

        // ---- validations ----
        if (productIds.isEmpty()) {
            throw new IllegalArgumentException("Cart is empty. Add at least one product.");
        }
        if (name.trim().isEmpty()) {
            throw new IllegalArgumentException("Customer name is required");
        }
        if (!phone.matches("\\d{10}")) {
            throw new IllegalArgumentException("Phone must be exactly 10 digits");
        }
        if (address.trim().isEmpty()) {
            throw new IllegalArgumentException("Delivery address is required");
        }
        if (!(channel.equals("ONLINE") || channel.equals("OFFLINE"))) {
            throw new IllegalArgumentException("Channel must be ONLINE or OFFLINE");
        }

        Session session = HibernateUtil.getSessionFactory().openSession();
        Transaction tx = session.beginTransaction();
        try {
            // ---- 1. find customer by phone, create if new ----
            Customer customer = session.createQuery("from Customer where phone = :phone", Customer.class)
                    .setParameter("phone", phone)
                    .uniqueResult();
            if (customer == null) {
                customer = new Customer(name.trim(), phone);
                session.save(customer);
            }

            // ---- 2. create the order ----
            CustomerOrder order = new CustomerOrder();
            order.setCustomer(customer);
            order.setChannel(channel);
            order.setDeliveryAddress(address.trim());
            order.setOrderDate(LocalDateTime.now());

            // ---- 3. add items and calculate subtotal ----
            double subtotal = 0;
            for (int i = 0; i < productIds.size(); i++) {
                Product product = session.get(Product.class, productIds.get(i));
                int qty = quantities.get(i);
                if (product == null) {
                    throw new IllegalArgumentException("Invalid product id: " + productIds.get(i));
                }
                OrderItem item = new OrderItem();
                item.setOrder(order);
                item.setProduct(product);
                item.setQuantity(qty);
                item.setUnitPrice(product.getPrice());
                item.setLineTotal(product.getPrice() * qty);
                order.getItems().add(item);
                subtotal = subtotal + item.getLineTotal();
            }

            // ---- 4. deposit and total ----
            double deposit = round2(subtotal * DEPOSIT_RATE);
            order.setSubtotal(round2(subtotal));
            order.setDepositAmount(deposit);
            order.setTotalPaid(round2(subtotal + deposit));
            session.save(order);   // order id is generated here, items are saved automatically

            // ---- 5. create the container for this order ----
            DepositContainer container = new DepositContainer();
            container.setOrder(order);
            container.setCode("CNT-" + order.getId());
            container.setDepositAmount(deposit);
            container.setStatus("PENDING");
            container.setIssuedAt(LocalDateTime.now());
            session.save(container);
            order.setContainer(container);

            tx.commit();
            return order;
        } catch (RuntimeException e) {
            tx.rollback();
            throw e;
        } finally {
            session.close();
        }
    }

    // keeps only 2 decimal places
    static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    static void returnContainer() {
        System.out.print("Container code (example CNT-1): ");
        String code = sc.nextLine().trim().toUpperCase();
        System.out.print("Returned at (store / location name): ");
        String location = sc.nextLine().trim();

        if (code.isEmpty()) {
            throw new IllegalArgumentException("Container code is required");
        }
        if (location.isEmpty()) {
            throw new IllegalArgumentException("Return location is required");
        }

        Session session = HibernateUtil.getSessionFactory().openSession();
        Transaction tx = session.beginTransaction();
        try {
            DepositContainer c = session
                    .createQuery("from DepositContainer where code = :code", DepositContainer.class)
                    .setParameter("code", code)
                    .uniqueResult();

            if (c == null) {
                throw new IllegalArgumentException("No container found with code: " + code);
            }
            if (c.getStatus().equals("RETURNED")) {
                throw new IllegalStateException("Container " + c.getCode()
                        + " is already returned. Refund already given.");
            }

            c.setStatus("RETURNED");
            c.setReturnedAt(LocalDateTime.now());
            c.setReturnLocation(location);
            c.setRefundAmount(c.getDepositAmount());
            tx.commit();

            System.out.println();
            System.out.println("Container " + c.getCode() + " returned at " + c.getReturnedAt().format(fmt));
            System.out.println("Customer : " + c.getOrder().getCustomer().getName());
            System.out.println("REFUND   : Rs." + c.getRefundAmount());
        } catch (RuntimeException e) {
            tx.rollback();
            throw e;
        } finally {
            session.close();
        }
    }

    // ------------------------------------------------------------
    // 4. VIEW ORDERS / PENDING / SUMMARY (no id needed)
    // ------------------------------------------------------------

    static void showAllOrders() {
        Session session = HibernateUtil.getSessionFactory().openSession();
        try {
            List<CustomerOrder> orders = session.createQuery("from CustomerOrder order by id",
                    CustomerOrder.class).list();
            if (orders.isEmpty()) {
                System.out.println("No orders yet.");
                return;
            }
            for (CustomerOrder o : orders) {
                printOrder(o);
            }
        } finally {
            session.close();
        }
    }

    static void showPendingContainers() {
        Session session = HibernateUtil.getSessionFactory().openSession();
        try {
            List<DepositContainer> list = session.createQuery(
                    "from DepositContainer where status = 'PENDING' order by id",
                    DepositContainer.class).list();
            if (list.isEmpty()) {
                System.out.println("No pending containers.");
                return;
            }
            System.out.println();
            System.out.println("Code     | Customer        | Phone      | Deposit");
            System.out.println("---------+-----------------+------------+---------");
            for (DepositContainer c : list) {
                System.out.printf("%-8s | %-15s | %-10s | %7.2f%n", c.getCode(),
                        c.getOrder().getCustomer().getName(), c.getOrder().getCustomer().getPhone(),
                        c.getDepositAmount());
            }
        } finally {
            session.close();
        }
    }

   
    static void showSummary() {
        Session session = HibernateUtil.getSessionFactory().openSession();
        try {
            List<DepositContainer> list = session.createQuery("from DepositContainer order by id",
                    DepositContainer.class).list();
            if (list.isEmpty()) {
                System.out.println("No data yet.");
                return;
            }

            double totalDeposit = 0;
            double totalRefund = 0;

            System.out.println();
            System.out.println("Code     | Customer        | Phone      | Deposit  | Refund   | Status");
            System.out.println("---------+-----------------+------------+----------+----------+--------------");
            for (DepositContainer c : list) {
                double refund = 0;
                String status = "NOT REFUNDED";
                if (c.getStatus().equals("RETURNED")) {
                    refund = c.getRefundAmount();
                    status = "REFUNDED";
                }
                totalDeposit = totalDeposit + c.getDepositAmount();
                totalRefund = totalRefund + refund;

                System.out.printf("%-8s | %-15s | %-10s | %8.2f | %8.2f | %s%n", c.getCode(),
                        c.getOrder().getCustomer().getName(), c.getOrder().getCustomer().getPhone(),
                        c.getDepositAmount(), refund, status);
            }
            System.out.println("---------+-----------------+------------+----------+----------+--------------");
            System.out.printf("Total deposit collected : %9.2f%n", totalDeposit);
            System.out.printf("Total refund given      : %9.2f%n", totalRefund);
            System.out.printf("Refund still pending    : %9.2f%n", totalDeposit - totalRefund);
        } finally {
            session.close();
        }
    }

    // prints one full order (receipt + container status)
    static void printOrder(CustomerOrder o) {
        System.out.println();
        System.out.println("========== ORDER " + o.getId() + " ==========");
        System.out.println("Channel   : " + o.getChannel());
        System.out.println("Customer  : " + o.getCustomer().getName() + " (" + o.getCustomer().getPhone() + ")");
        System.out.println("Address   : " + o.getDeliveryAddress());
        System.out.println("Date      : " + o.getOrderDate().format(fmt));
        System.out.println("-----------------------------------");
        for (OrderItem i : o.getItems()) {
            System.out.printf("%-22s %2d x %7.2f = %8.2f%n",
                    i.getProduct().getName(), i.getQuantity(), i.getUnitPrice(), i.getLineTotal());
        }
        System.out.println("-----------------------------------");
        System.out.printf("Subtotal              : %9.2f%n", o.getSubtotal());
        System.out.printf("Container deposit 10%% : %9.2f%n", o.getDepositAmount());
        System.out.printf("TOTAL PAID            : %9.2f%n", o.getTotalPaid());

        DepositContainer c = o.getContainer();
        if (c != null) {
            System.out.println("Container code        : " + c.getCode());
            System.out.println("Container status      : " + c.getStatus());
            if (c.getStatus().equals("RETURNED")) {
                System.out.println("Refunded              : Rs." + c.getRefundAmount()
                        + " on " + c.getReturnedAt().format(fmt) + " at " + c.getReturnLocation());
            }
        }
        System.out.println("===================================");
    }
}
