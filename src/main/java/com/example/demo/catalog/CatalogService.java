package com.example.demo.catalog;

import static com.example.demo.catalog.CatalogDtos.*;
import static org.springframework.http.HttpStatus.*;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.io.*;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly=true)
public class CatalogService {
    private final CatalogRepository repo;
    private final Clock clock;
    private static final ZoneId ZONE=ZoneId.of("America/Sao_Paulo");
    public CatalogService(CatalogRepository repo,Clock clock) {this.repo=repo;this.clock=clock;}
    private LocalDate today() {return LocalDate.now(clock.withZone(ZONE));}
    public void requireCatalogOwner(UUID owner) {
        if(repo.ownerMarket(owner,false).isEmpty()) throw new ResponseStatusException(FORBIDDEN,"O catálogo é exclusivo para contas com mercado cadastrado.");
    }
    public List<CatalogProduct> suggestions(UUID owner,String query,int limit) {
        requireCatalogOwner(owner);
        String normalized=normalize(query);
        require(normalized.length()>=2,"Digite pelo menos 2 caracteres para pesquisar.");
        return repo.suggestions(normalized,limit);
    }
    public CatalogRepository.ProductImage catalogImage(UUID owner,UUID id) {
        requireCatalogOwner(owner);
        return repo.image(id).orElseThrow(this::missing);
    }
    @Transactional public OwnerProduct fromCatalog(UUID owner,FromCatalogInput input) {
        requireCatalogOwner(owner);
        var market=ownMarket(owner,true);
        var product=input.product();validateProduct(product);
        try {
            var source=repo.catalogSource(input.sourceProductId()).orElseThrow(()->new ResponseStatusException(NOT_FOUND,"O produto de origem não existe mais. Escolha outra sugestão ou cadastre manualmente."));
            UUID id=UUID.randomUUID();
            repo.createProduct(id,market.id(),product,normalize(product.name()));
            if(source.content()!=null) repo.saveImage(id,source.contentType(),source.content());
            return repo.product(id).orElseThrow().product();
        } catch(org.springframework.dao.DataAccessException e) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE,"Não foi possível copiar o cadastro e sua foto. Nenhuma oferta foi criada. Tente novamente.");
        }
    }
    @Transactional public int expireProducts() {return repo.expireProducts(today());}
    private ResponseStatusException missing() {return new ResponseStatusException(NOT_FOUND,"Registro não encontrado.");}
    private void require(boolean valid,String message) {if(!valid) throw new ResponseStatusException(BAD_REQUEST,message);}
    public List<Category> categories() {return repo.categories();}
    public List<PublicMarket> markets(int limit,int offset) {return repo.markets(limit,offset).stream().map(this::publicMarket).toList();}
    public PublicMarket market(UUID id) {return publicMarket(repo.market(id).orElseThrow(this::missing));}
    private PublicMarket publicMarket(CatalogRepository.MarketRow m) {
        var p=m.profile(); var stats=repo.marketStats(m.id(),today());
        return new PublicMarket(m.id(),p.marketName(),"","mercado",p.city()+" · "+p.state(),stats[0],stats[0]+" itens",
            stats[1]>0?"até "+stats[1]+"%":"Sem ofertas",p.address()+", "+p.addressNumber()+" - "+p.neighborhood(),repo.hours(m),repo.marketImageUri(m.id()),p.marketPhone());
    }
    public OwnerMarket ownerMarket(UUID owner) {return ownerResponse(ownMarket(owner,false));}
    private CatalogRepository.MarketRow ownMarket(UUID owner,boolean lock) {
        return repo.ownerMarket(owner,lock).orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Cadastre seu mercado antes de gerenciar produtos."));
    }
    private OwnerMarket ownerResponse(CatalogRepository.MarketRow m) {return new OwnerMarket(m.id(),m.profile(),repo.hours(m),repo.marketImageUri(m.id()));}
    public CatalogRepository.ProductImage marketImage(UUID id) {
        return repo.marketImage(id).orElseThrow(this::missing);
    }
    @Transactional public OwnerMarket marketImage(UUID owner,MultipartFile file) {
        var market=ownMarket(owner,true);
        if(file.getSize()>5L*1024*1024)
            throw new ResponseStatusException(PAYLOAD_TOO_LARGE,"Envie uma imagem de até 5 MB.");
        require(!file.isEmpty(),"Envie uma imagem JPEG ou PNG não vazia.");
        try {
            byte[] bytes=file.getBytes();
            String type;
            try(var stream=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers=ImageIO.getImageReaders(stream);
                require(readers.hasNext(),"Imagem inválida. Use JPEG ou PNG.");
                var reader=readers.next();
                try {
                    reader.setInput(stream);
                    String format=reader.getFormatName().toLowerCase(Locale.ROOT);
                    require(format.equals("jpeg")||format.equals("png"),"Use uma imagem JPEG ou PNG.");
                    int width=reader.getWidth(0),height=reader.getHeight(0);
                    require(width>0 && height>0 && (long)width*height<=16000000,"A imagem deve ter até 16 megapixels.");
                    // Decode pixel data, not just the header. JPEG readers may recover corrupt files with a warning.
                    boolean[] warning={false};
                    reader.addIIOReadWarningListener((source,message)->warning[0]=true);
                    var decoded=reader.read(0);
                    require(decoded!=null && !warning[0],"A imagem está corrompida ou incompleta.");
                    decoded.flush();
                    type=format.equals("jpeg")?"image/jpeg":"image/png";
                } finally {reader.dispose();}
            }
            repo.saveMarketImage(market.id(),type,bytes);
            return ownerResponse(market);
        } catch(IOException e) {throw new ResponseStatusException(BAD_REQUEST,"Não foi possível ler a imagem.");}
    }
    @Transactional public OwnerMarket createMarket(UUID owner,MarketInput p) {
        if(repo.ownerMarket(owner,false).isPresent()) throw new ResponseStatusException(CONFLICT,"Você já possui um mercado cadastrado.");
        validateMarket(p);repo.createMarket(UUID.randomUUID(),owner,p);return ownerMarket(owner);
    }
    @Transactional public OwnerMarket updateMarket(UUID owner,MarketInput p) {
        var m=ownMarket(owner,true);validateMarket(p);repo.updateMarket(m.id(),p);return ownerMarket(owner);
    }
    private void validateMarket(MarketInput p) {require(p.document().replaceAll("\\D","").length()==14,"Informe um CNPJ com 14 dígitos.");}
    @Transactional public OwnerMarket hours(UUID owner,HoursInput input) {
        var m=ownMarket(owner,true); var days=input.days();
        require(days.stream().map(DayHours::day).distinct().count()==7,"Informe os sete dias da semana sem repetições.");
        List<int[]> intervals=new ArrayList<>();
        for(var day:days) if(day.enabled()) {
            require(!day.periods().isEmpty(),"Informe ao menos um período para cada dia aberto.");
            for(var period:day.periods()) {
                int start=LocalTime.parse(period.opensAt()).toSecondOfDay()/60;
                int end=LocalTime.parse(period.closesAt()).toSecondOfDay()/60;
                require(start!=end,"Abertura e fechamento devem ser diferentes.");
                if(end<start) end+=1440;
                intervals.add(new int[]{day.day()*1440+start,day.day()*1440+end});
                intervals.add(new int[]{day.day()*1440+start+10080,day.day()*1440+end+10080});
            }
        }
        intervals.sort(Comparator.comparingInt(i->i[0]));
        for(int i=1;i<intervals.size();i++) require(intervals.get(i)[0]>=intervals.get(i-1)[1],"Os horários não podem se sobrepor.");
        repo.saveHours(m.id(),days);return ownerMarket(owner);
    }
    public List<OwnerProduct> ownerProducts(UUID owner,int limit,int offset) {
        return repo.ownerProducts(ownMarket(owner,false).id(),limit,offset).stream().map(CatalogRepository.ProductRow::product).toList();
    }
    public OwnerProduct ownerProduct(UUID owner,UUID id) {return owned(owner,id,false).product();}
    private CatalogRepository.ProductRow owned(UUID owner,UUID id,boolean lock) {
        var market=ownMarket(owner,lock);var row=repo.product(id).orElseThrow(this::missing);
        if(!row.marketId().equals(market.id())) throw missing();
        return row;
    }
    @Transactional public OwnerProduct createProduct(UUID owner,ProductInput p) {
        var market=ownMarket(owner,true);validateProduct(p);UUID id=UUID.randomUUID();
        repo.createProduct(id,market.id(),p,normalize(p.name()));return repo.product(id).orElseThrow().product();
    }
    @Transactional public OwnerProduct updateProduct(UUID owner,UUID id,ProductInput p) {
        owned(owner,id,true);validateProduct(p);repo.expireProduct(id,today());repo.updateProduct(id,p,normalize(p.name()));return repo.product(id).orElseThrow().product();
    }
    private void validateProduct(ProductInput p) {
        require(repo.categoryExists(p.categoryId()),"Categoria inválida.");
        require(p.offerPrice().compareTo(p.originalPrice())<0,"A oferta deve ser menor que o preço original.");
        require(!p.expirationDate().isBefore(today()),"A validade não pode estar no passado.");
        require(p.manufactureDate()==null || (!p.manufactureDate().isAfter(p.expirationDate()) && !p.manufactureDate().isAfter(today())),"Data de fabricação inválida.");
    }
    @Transactional public OwnerProduct active(UUID owner,UUID id,boolean active) {
        var row=owned(owner,id,true);
        if(active) require(!row.product().expirationDate().isBefore(today()),"Um produto vencido não pode ser ativado.");
        repo.active(id,active);return repo.product(id).orElseThrow().product();
    }
    @Transactional public void delete(UUID owner,UUID id) {owned(owner,id,true);repo.deleteProduct(id);}
    public List<PublicProduct> products(UUID market,String query,String category,int limit,int offset) {
        if(market!=null) repo.market(market).orElseThrow(this::missing);
        return repo.publicProducts(market,normalize(query),category,today(),limit,offset).stream().map(this::publicProduct).toList();
    }
    public PublicProduct product(UUID id) {
        var row=repo.product(id).orElseThrow(this::missing); if(!visible(row.product())) throw missing();return publicProduct(row);
    }
    private boolean visible(OwnerProduct p) {return p.isActive() && p.stockQuantity()>0 && !p.expirationDate().isBefore(today());}
    private PublicProduct publicProduct(CatalogRepository.ProductRow row) {
        var p=row.product();long days=ChronoUnit.DAYS.between(today(),p.expirationDate());
        int discount=p.originalPrice().subtract(p.offerPrice()).multiply(java.math.BigDecimal.valueOf(100))
            .divide(p.originalPrice(),0,RoundingMode.HALF_UP).intValue();
        var currency=NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"));
        return new PublicProduct(p.id(),p.name(),p.categoryId(),p.expirationDate(),days==0?"Hoje":days==1?"Amanhã":days+" dias",
            days<=1,days>=7,p.stockQuantity(),discount,"-"+discount+"%",p.originalPrice(),currency.format(p.originalPrice()).replace('\u00a0',' '),
            p.offerPrice(),currency.format(p.offerPrice()).replace('\u00a0',' '),p.imageUri(),row.marketId(),row.marketName(),row.location());
    }
    public CatalogRepository.ProductImage image(UUID id,UUID owner) {
        var row=repo.product(id).orElseThrow(this::missing);
        if(!visible(row.product())) {
            if(owner==null) throw missing();owned(owner,id,false);
        }
        return repo.image(id).orElseThrow(this::missing);
    }
    @Transactional public OwnerProduct image(UUID owner,UUID id,MultipartFile file) {
        owned(owner,id,true);
        require(!file.isEmpty() && file.getSize()<=5*1024*1024,"Envie uma imagem de até 5 MB.");
        try {
            byte[] bytes=file.getBytes();String type;
            try(var stream=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers=ImageIO.getImageReaders(stream);require(readers.hasNext(),"Imagem inválida. Use JPEG ou PNG.");
                var reader=readers.next();
                try {
                    reader.setInput(stream);String format=reader.getFormatName().toLowerCase(Locale.ROOT);
                    require(format.equals("jpeg")||format.equals("png"),"Use uma imagem JPEG ou PNG.");
                    require((long)reader.getWidth(0)*reader.getHeight(0)<=16000000,"A imagem deve ter até 16 megapixels.");
                    type=format.equals("jpeg")?"image/jpeg":"image/png";
                } finally {reader.dispose();}
            }
            repo.saveImage(id,type,bytes);return repo.product(id).orElseThrow().product();
        } catch(IOException e) {throw new ResponseStatusException(BAD_REQUEST,"Não foi possível ler a imagem.");}
    }
    static String normalize(String value) {
        return value==null?"":Normalizer.normalize(value,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).trim();
    }
}
