package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestNormalizeSelectNotAnyFilter(t *testing.T) {
	rule := loadRule(t, "NormalizeSelectNotAnyFilter")
	e := newEnv(t)
	input, ic := e.scan("u", 1)
	sub, sc := e.scan("t", 1)
	membership := e.f.ConstructAny(sub, e.v(ic[0]), &memo.SubqueryPrivate{Cmp: opt.EqOp})
	before := e.f.ConstructSelect(input, e.filters(e.f.ConstructNot(membership)))
	matched := e.f.ConstructEq(e.v(ic[0]), e.v(sc[0]))
	correlated := e.f.ConstructSelect(sub, e.filters(e.f.ConstructOr(matched, e.f.ConstructIs(matched, memo.NullSingleton))))
	want := e.f.ConstructSelect(input, e.filters(e.f.ConstructNot(e.exists(correlated))))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(input, e.filters(membership)))
	e.expectNoMatch(rule, e.f.ConstructSelect(input, e.filters(e.f.ConstructGt(e.v(ic[0]), e.num(0)))))
}

func TestSQLNormalizeSelectNotAnyFilter(t *testing.T) {
	expectSQLFires(t, "NormalizeSelectNotAnyFilter", "SELECT x FROM u WHERE x NOT IN (SELECT a FROM t)")
}
