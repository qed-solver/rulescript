package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestGroupBasedRowLevelOperationScanPlanning(t *testing.T) {
	rule := loadRule(t, "GroupBasedRowLevelOperationScanPlanning")
	e := newEnv(t)
	tgt, tc := e.scan("t", 3)
	oth, oc := e.scan("u", 2)
	eval1 := e.f.ConstructEq(e.v(tc[0]), e.num(1))
	eval2 := e.f.ConstructEq(e.v(tc[1]), e.num(2))
	rest := e.f.ConstructEq(e.v(tc[2]), e.v(oc[1]))
	before := e.f.ConstructInnerJoin(tgt, oth, e.filters(eval1, eval2, rest), &memo.JoinPrivate{})
	sel := e.f.ConstructSelect(tgt, e.filters(eval1, eval2))
	want := e.f.ConstructInnerJoin(sel, oth, e.filters(rest), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructLeftJoin(tgt, oth, e.filters(eval1, eval2, rest), &memo.JoinPrivate{}))
	e.expectRewrite(rule, e.f.ConstructInnerJoin(tgt, oth, e.filters(eval1, eval2), &memo.JoinPrivate{}),
		e.f.ConstructInnerJoin(sel, oth, nil, &memo.JoinPrivate{}))
	otherSide := e.f.ConstructEq(e.v(oc[0]), e.num(1))
	e.expectRewrite(rule, e.f.ConstructInnerJoin(tgt, oth, e.filters(otherSide, eval2, rest), &memo.JoinPrivate{}),
		e.f.ConstructInnerJoin(e.f.ConstructSelect(tgt, e.filters(eval2)), oth, e.filters(otherSide, rest), &memo.JoinPrivate{}))
}
