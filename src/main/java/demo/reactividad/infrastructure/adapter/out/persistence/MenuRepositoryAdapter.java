package demo.reactividad.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import demo.reactividad.application.port.out.MenuRepositoryPort;
import demo.reactividad.domain.model.Menu;
import demo.reactividad.infrastructure.adapter.out.persistence.entity.MenuEntity;
import demo.reactividad.infrastructure.adapter.out.persistence.mapper.MenuPersistenceMapper;
import demo.reactividad.infrastructure.adapter.out.persistence.repository.MenuR2dbcRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class MenuRepositoryAdapter implements MenuRepositoryPort {

    private final MenuR2dbcRepository menuR2dbcRepository;
    private final MenuPersistenceMapper menuPersistenceMapper;

    @Override
    public Mono<Menu> findById(UUID menuId) {
        return this.menuR2dbcRepository.findById(menuId)
                .map(this.menuPersistenceMapper::toDomain);
    }

    @Override
    public Mono<Menu> save(Menu menu) {
        return this.menuR2dbcRepository.save(this.menuPersistenceMapper.toEntity(menu))
                .map(this.menuPersistenceMapper::toDomain);
    }

    @Override
    public Mono<Void> deleteById(UUID menuId) {
        return this.menuR2dbcRepository.deleteById(menuId);
    }

    @Override
    public Flux<Menu> saveAll(List<Menu> menus) {
        List<MenuEntity> entities = menus.stream()
                .map(this.menuPersistenceMapper::toEntity)
                .toList();
        return this.menuR2dbcRepository.saveAll(entities)
                .map(this.menuPersistenceMapper::toDomain);
    }
}
