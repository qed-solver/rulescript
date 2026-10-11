package rulescript_test

import (
	"testing"
)

func TestDeduplicateSelectFilters(t *testing.T) {
	rule := loadRule(t, "DeduplicateSelectFilters")
	e := newEnv(t)
	s, cols := e.scan("u", 2)
	p := e.f.ConstructGt(e.v(cols[0]), e.num(0))
	q := e.f.ConstructLt(e.v(cols[1]), e.num(5))
	r := e.f.ConstructGt(e.v(cols[0]), e.num(1))
	first := e.f.ConstructSelect(s, e.filters(p))
	second := e.f.ConstructSelect(first, e.filters(q))
	e.expectRewrite(rule, e.f.ConstructSelect(second, e.filters(p)), second)
	e.expectNoMatch(rule, e.f.ConstructSelect(second, e.filters(r)))
	e.expectNoMatch(rule, e.f.ConstructSelect(e.f.ConstructSelect(s, e.filters(q)), e.filters(p)))
}

func TestDeduplicateSelectFiltersSQL(t *testing.T) {
	sql := "SELECT * FROM (SELECT * FROM (SELECT * FROM (VALUES (1, 2), (3, 4)) AS v(x,y) WHERE x > 0) WHERE y < 5) WHERE x > 0"
	plan, fired := planFor(t, sql, nil, "DeduplicateSelectFilters")
	if len(fired) == 0 {
		t.Fatalf("DeduplicateSelectFilters did not fire on %s\n%s", sql, plan)
	}
}
