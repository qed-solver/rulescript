package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestFilterTableFunctionTranspose(t *testing.T) {
	rule := loadRule(t, "FilterTableFunctionTranspose")
	e := newEnv(t)
	l, lc := e.scan("u", 2)
	r, rc := e.scan("t", 1)
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
	pass := opt.MakeColSet(lc[0], lc[1])
	pred := func(in memo.RelExpr) memo.RelExpr {
		return e.f.ConstructSelect(in, e.filters(e.f.ConstructLt(e.v(lc[0]), e.v(lc[1]))))
	}
	join := e.f.ConstructInnerJoin(l, r, on, &memo.JoinPrivate{})
	before := pred(e.f.ConstructProject(join, memo.EmptyProjectionsExpr, pass))
	after := e.f.ConstructProject(
		e.f.ConstructInnerJoin(pred(l), r, on, &memo.JoinPrivate{}),
		memo.EmptyProjectionsExpr, pass,
	)
	e.expectRewrite(rule, before, after)

	left := e.f.ConstructLeftJoin(l, r, on, &memo.JoinPrivate{})
	e.expectNoMatch(rule, pred(e.f.ConstructProject(left, memo.EmptyProjectionsExpr, pass)))

	e.expectNoMatch(rule, e.f.ConstructProject(pred(join), memo.EmptyProjectionsExpr, pass))

	w, wc := e.scan("nn3", 3)
	wjoin := e.f.ConstructInnerJoin(w, r, e.filters(e.f.ConstructEq(e.v(wc[0]), e.v(rc[0]))), &memo.JoinPrivate{})
	wpass := opt.MakeColSet(wc[0], wc[1])
	e.expectNoMatch(rule, e.f.ConstructSelect(
		e.f.ConstructProject(wjoin, memo.EmptyProjectionsExpr, wpass),
		e.filters(e.f.ConstructLt(e.v(wc[0]), e.v(wc[1]))),
	))
}
