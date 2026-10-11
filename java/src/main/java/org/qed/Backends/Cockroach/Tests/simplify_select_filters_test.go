package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestSimplifySelectFilters(t *testing.T) {
	rule := loadRule(t, "SimplifySelectFilters")
	e := newEnv(t)
	s, cols := e.scan("u", 1)
	p := e.f.ConstructGt(e.v(cols[0]), e.num(0))
	nullBool := e.f.ConstructNull(types.Bool)
	e.expectRewrite(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructOr(p, nullBool))), e.f.ConstructSelect(s, e.filters(p)))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructOr(nullBool, p))))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructOr(p, e.f.ConstructFalse()))))
}

func TestSQLSimplifySelectFilters(t *testing.T) {
	expectSQLFires(t, "SimplifySelectFilters", "SELECT x FROM u WHERE x > 0 OR NULL")
}
