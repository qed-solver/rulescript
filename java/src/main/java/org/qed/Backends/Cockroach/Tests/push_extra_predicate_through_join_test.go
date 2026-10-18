package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestPushExtraPredicateThroughJoin(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "PushExtraPredicateThroughJoin")

	left, leftCols := e.scan("t", 1)
	right, rightCols := e.scan("u", 1)
	pLeft := e.f.ConstructEq(e.v(leftCols[0]), e.num(1))
	qRight := e.f.ConstructEq(e.v(rightCols[0]), e.num(2))
	sRight := e.f.ConstructEq(e.v(rightCols[0]), e.num(3))
	condition := e.f.ConstructOr(
		e.f.ConstructAnd(pLeft, qRight),
		e.f.ConstructAnd(pLeft, sRight),
	)
	before := e.f.ConstructInnerJoin(left, right, e.filters(condition), &memo.JoinPrivate{})

	wantLeft := e.f.ConstructSelect(left, e.filters(e.f.ConstructOr(pLeft, pLeft)))
	wantRight := e.f.ConstructSelect(right, e.filters(e.f.ConstructOr(qRight, sRight)))
	wantCondition := e.f.ConstructOr(
		e.f.ConstructAnd(pLeft, qRight),
		e.f.ConstructAnd(pLeft, sRight),
	)
	want := e.f.ConstructInnerJoin(wantLeft, wantRight, e.filters(wantCondition), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)

	nearMiss := e.f.ConstructLeftJoin(left, right, e.filters(condition), &memo.JoinPrivate{})
	e.expectNoMatch(rule, nearMiss)
}
