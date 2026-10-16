package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestCommuteNullIs(t *testing.T) {
	rule := loadRule(t, "CommuteNullIs")
	e := newEnv(t)
	s, cols := e.scan("u", 1)
	null := e.f.ConstructNull(types.Int)
	before := e.f.ConstructSelect(s, e.filters(e.f.ConstructIs(null, e.v(cols[0]))))
	want := e.f.ConstructSelect(s, e.filters(e.f.ConstructIs(e.v(cols[0]), null)))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructIs(e.num(1), e.v(cols[0])))))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructIsNot(null, e.v(cols[0])))))
}

func TestSQLCommuteNullIs(t *testing.T) {
	expectSQLFires(t, "CommuteNullIs", "SELECT x FROM u WHERE NULL IS NOT DISTINCT FROM x")
}
